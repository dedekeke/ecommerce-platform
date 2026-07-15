package com.ecommerce.notificationservice.kafka.dedup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Idempotency ledger for the customer-facing notification consumers
 * ({@code OrderEventConsumer}, {@code RefundEventConsumer},
 * {@code RmaEventConsumer}, {@code CartAbandonedConsumer}).
 *
 * <p><b>Why this exists.</b> Those consumers previously deduplicated with a
 * check-then-insert against the notification log
 * ({@code existsByRelatedEntityIdAndTemplateCodeAndStatusIn} then send). With
 * more than one replica — or a concurrent Kafka redelivery — two threads can
 * both pass the existence check before either has written a log row, so both
 * send. The result is duplicate customer emails/SMS. This is the same class of
 * race fixed for the loyalty consumer in PR#119.
 *
 * <p><b>The guard.</b> The {@code _id} of a MongoDB document is backed by a
 * native, always-present unique index, enforced atomically by the server
 * regardless of how many application replicas insert concurrently. We make the
 * dedup key the {@code _id} and <em>insert</em> (never upsert) via
 * {@link ProcessedNotificationEventRepository}. Exactly one concurrent insert of
 * a given key wins; every other insert fails with
 * {@link org.springframework.dao.DuplicateKeyException} (a subclass of
 * {@code DataIntegrityViolationException}) — the MongoDB analogue of the
 * relational unique-constraint violation PR#119 relies on. The caller treats
 * that as an idempotent success. This is why the fix is insert-first rather than
 * check-first: the write itself is the mutual-exclusion primitive.
 *
 * <p><b>Key shape.</b> {@code <templateCode>:<entityId>} (e.g.
 * {@code ORDER_CONFIRMATION:order-123}). This preserves the exact dedup
 * granularity the consumers had before — one delivery per (template, business
 * entity) — which is the customer-facing invariant ("do not email the same
 * order confirmation twice"), stronger for that goal than a raw per-event id.
 *
 * <p><b>No Flyway/migration.</b> notification-service is MongoDB-backed, so
 * there is no relational schema and no Flyway migration to add. The uniqueness
 * guarantee is the collection's native {@code _id} index, created by the server
 * on first write; no explicit index declaration is required.
 */
@Document(collection = "processed_notification_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedNotificationEvent {

    /** Composite dedup key {@code <templateCode>:<entityId>}; the unique guard. */
    @Id
    private String id;

    /** Kafka topic the event was consumed from — for audit/debugging only. */
    private String topic;

    /** When the claim was recorded. */
    private Instant consumedAt;
}
