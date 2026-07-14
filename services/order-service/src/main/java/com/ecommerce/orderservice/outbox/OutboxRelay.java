package com.ecommerce.orderservice.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polling outbox relay.
 *
 * <p>Strategy: every {@code outbox.relay.poll-interval-ms} milliseconds we
 * (1) claim a batch of unpublished rows with {@code FOR UPDATE SKIP LOCKED}
 * in a short transaction, (2) publish them to Kafka <em>outside</em> any DB
 * transaction, then (3) mark the published rows in a second transaction. A
 * failed Kafka publish leaves the row unpublished so the next poll re-attempts;
 * the per-row {@code attempt_count} captures retries so stuck events can be
 * surfaced operationally.
 *
 * <p><b>Replica safety.</b> {@code SKIP LOCKED} lets multiple service instances
 * run the relay concurrently: each claim locks a disjoint set of rows, so two
 * instances never publish the same row in the common case. Crucially, the DB
 * connection is <em>not</em> held across the blocking Kafka send — the claim
 * transaction commits first — so a slow broker cannot starve the Hikari pool.
 *
 * <p>A claimed row stays {@code published_at IS NULL} until {@code markPublished}
 * runs <em>after</em> the whole batch has been sent, so between the claim commit
 * and that final update another instance can re-claim and re-publish rows in the
 * batch. This window is normally sub-second, but under a degraded broker it
 * stretches across the batch's Kafka sends (up to {@code batch-size} ×
 * {@value #KAFKA_SEND_TIMEOUT_SECONDS}s) — i.e. it is bounded but not "narrow".
 * At-least-once delivery is therefore expected by design; the consumer-side
 * {@code outbox-event-id} dedup is the safety net that makes it exactly-once for
 * consumers.
 *
 * <p><b>Ordering.</b> A single claim is created-at ordered and published
 * sequentially (blocking on each send), and the Kafka key is the aggregate id
 * so events for one aggregate land on one partition. Strict cross-instance
 * per-aggregate ordering was never guaranteed (the previous single-transaction
 * relay only ordered within one instance) and is not introduced here.
 *
 * <p>Disabled in test profile via {@code outbox.relay.enabled=false} so
 * tests can exercise the relay deterministically by calling {@link #relay()}
 * synchronously instead of racing with the scheduler.
 *
 * <p>Migration note: when Postgres logical replication and Kafka Connect
 * become available in the target environment, this component is the swap
 * point — the table and consumer-side dedup contract stay identical and
 * Debezium's outbox event router replaces the polling loop.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class OutboxRelay {

    private static final String EVENT_ID_HEADER = "outbox-event-id";
    private static final String EVENT_TYPE_HEADER = "outbox-event-type";
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 10L;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxRelay(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        @Value("${outbox.relay.batch-size:100}") int batchSize
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    /**
     * Single relay tick: claim (tx1) → publish (no tx) → mark published (tx2).
     *
     * <p>Deliberately NOT {@code @Transactional} at the method level — holding a
     * transaction (and its pooled connection) across the blocking Kafka sends is
     * exactly the failure mode this restructure removes. Each repository call
     * below opens its own short transaction.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:500}")
    public void relay() {
        Pageable page = PageRequest.of(0, batchSize);
        List<OutboxEvent> batch = outboxRepository.claimUnpublishedForUpdate(page);
        if (batch.isEmpty()) {
            return;
        }

        log.debug("Outbox relay claimed {} events", batch.size());

        List<Long> publishedIds = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            if (publish(event)) {
                publishedIds.add(event.getId());
            }
        }

        if (!publishedIds.isEmpty()) {
            int updated = outboxRepository.markPublished(publishedIds, LocalDateTime.now());
            log.info("Outbox relay published {} / {} events ({} marked)",
                publishedIds.size(), batch.size(), updated);
        } else {
            log.warn("Outbox relay failed to publish any of {} claimed events", batch.size());
        }
    }

    /**
     * Publish one event to Kafka. Returns true on success, false on transient
     * failure so the caller can leave the row unpublished for retry.
     */
    private boolean publish(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
            event.getTopic(),
            null,
            event.getAggregateId(),
            event.getPayload()
        );
        record.headers().add(new RecordHeader(
            EVENT_ID_HEADER, event.getEventId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
            EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8)));

        try {
            // Block briefly so we know the broker accepted the record before
            // marking it published. acks=all on the producer guarantees
            // durability across replicas at this point.
            kafkaTemplate.send(record).get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordFailure(event, "interrupted");
            return false;
        } catch (ExecutionException | TimeoutException e) {
            String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            recordFailure(event, reason);
            return false;
        } catch (RuntimeException e) {
            recordFailure(event, e.getMessage());
            return false;
        }
    }

    private void recordFailure(OutboxEvent event, String reason) {
        int attempt = event.getAttemptCount() + 1;
        outboxRepository.recordFailure(event.getId(), attempt, truncate(reason));
        log.error("Outbox publish failed for event {} (attempt={}): {}",
            event.getEventId(), attempt, reason);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1024 ? s : s.substring(0, 1024);
    }
}
