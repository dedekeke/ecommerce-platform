package com.ecommerce.promotionservice.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Idempotency ledger for the loyalty {@code order.completed} consumer.
 *
 * <p>Stores the {@code outbox-event-id} of every {@code order.completed} event
 * whose spend has already been accumulated. A redelivery (Kafka at-least-once,
 * retry after crash, or the outbox relay re-shipping a row it could not mark
 * SENT) is dropped instead of double-counting the user's lifetime spend —
 * which would otherwise inflate their loyalty tier and hand out unearned
 * discounts.
 *
 * <p>This is the consumer-side exactly-once safety net that the order-service
 * transactional-outbox redesign (PR#112) relies on: the relay guarantees
 * at-least-once delivery with a stable per-event id in the
 * {@code outbox-event-id} header, and each consumer is responsible for
 * suppressing duplicates. It mirrors the choreography dedup pattern already
 * used by {@link com.ecommerce.promotionservice.saga.replenishment.ConsumedReplenishmentEvent}.
 *
 * <p>The id is stored as an opaque {@code VARCHAR} rather than a {@code UUID}
 * so non-UUID legacy/manual publishers do not blow up on parsing.
 */
@Entity
@Table(name = "promotion_processed_loyalty_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedLoyaltyEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;
}
