package com.ecommerce.inventoryservice.outbox;

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
 * run the relay concurrently, each claiming a disjoint set of rows, and the DB
 * connection is never held across the blocking Kafka send. Rows stay unpublished
 * until the batch's sends complete, so duplicates are possible in that interval
 * (bounded, not "narrow", under a slow broker) and are absorbed by consumer-side
 * {@code outbox-event-id} dedup. See order-service's copy for full design notes.
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
     * NOT {@code @Transactional} — the DB connection must not be held across the
     * blocking Kafka sends.
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
