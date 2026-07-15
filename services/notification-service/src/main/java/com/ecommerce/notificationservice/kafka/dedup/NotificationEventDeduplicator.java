package com.ecommerce.notificationservice.kafka.dedup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Insert-first idempotency guard shared by the notification Kafka consumers.
 *
 * <p>Lives in its own bean (mirroring PR#119's {@code OrderCompletedProcessor}
 * split) so the claim is a single, independently testable unit with one
 * responsibility: atomically stake a claim on a dedup key.
 *
 * <p>Unlike PR#119 there is no surrounding {@code @Transactional}: this service
 * is MongoDB-backed (standalone, no multi-document transactions) and the
 * side-effect being guarded is an outbound email/SMS, which is not
 * transactional and cannot be rolled back. The atomicity that matters — "only
 * one delivery of this event proceeds" — is provided entirely by the unique
 * {@code _id} index and the insert-first write below. Send failures are not
 * retried via Kafka redelivery; they are persisted as FAILED/RETRYING
 * notification logs and retried by {@code NotificationRetryScheduler}, so
 * claiming before sending does not lose notifications.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventDeduplicator {

    private final ProcessedNotificationEventRepository repository;

    /**
     * Atomically claim {@code dedupKey}. Returns {@code true} if this caller won
     * the claim and must perform the side-effect; {@code false} if the key was
     * already processed (a duplicate/redelivery) and the caller must skip.
     *
     * <p>Uses {@link ProcessedNotificationEventRepository#insert} (never
     * {@code save}, which would upsert and silently mask duplicates) so a
     * duplicate {@code _id} fails with {@link DuplicateKeyException}, caught here
     * and reported as {@code false}. Any other data-access failure (e.g. Mongo
     * unreachable) propagates so the container's error handler can retry / route
     * to the DLT rather than silently dropping the event.
     *
     * @param dedupKey composite {@code <templateCode>:<entityId>} key
     * @param topic    originating Kafka topic (audit only)
     * @return {@code true} if newly claimed, {@code false} if already processed
     */
    public boolean claim(String dedupKey, String topic) {
        try {
            repository.insert(ProcessedNotificationEvent.builder()
                    .id(dedupKey)
                    .topic(topic)
                    .consumedAt(Instant.now())
                    .build());
            return true;
        } catch (DuplicateKeyException duplicate) {
            log.warn("Duplicate event on topic={} key={} — already processed, skipping", topic, dedupKey);
            return false;
        }
    }
}
