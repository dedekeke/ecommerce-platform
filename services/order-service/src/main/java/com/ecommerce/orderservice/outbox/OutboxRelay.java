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
import org.springframework.transaction.annotation.Transactional;

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
 * claim a batch of unpublished events, publish them to Kafka with the
 * {@code outbox-event-id} header for consumer dedup, then mark the rows
 * published. A failed Kafka publish leaves the row unpublished so the next
 * poll re-attempts; the per-row {@code attempt_count} captures retries so
 * stuck events can be surfaced operationally.
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
     * Single relay tick. Claim batch, publish, mark published.
     *
     * <p>Wrapped in a transaction so the {@code markPublished} update flushes
     * atomically with the read. If the Kafka send fails for a row we keep
     * iterating — the row stays unpublished and the next tick retries it.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:500}")
    @Transactional
    public void relay() {
        Pageable page = PageRequest.of(0, batchSize);
        List<OutboxEvent> batch = outboxRepository.findUnpublished(page);
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
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(truncate(reason));
        outboxRepository.save(event);
        log.error("Outbox publish failed for event {} (attempt={}): {}",
            event.getEventId(), event.getAttemptCount(), reason);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1024 ? s : s.substring(0, 1024);
    }
}
