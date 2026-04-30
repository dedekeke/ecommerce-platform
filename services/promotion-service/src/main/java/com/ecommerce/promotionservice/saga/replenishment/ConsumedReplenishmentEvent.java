package com.ecommerce.promotionservice.saga.replenishment;

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
import java.util.UUID;

/**
 * Idempotency ledger for replenishment-saga events.
 *
 * <p>Stores the {@code eventId} of every successfully processed event so a
 * replay (Kafka at-least-once delivery, retry after crash, malicious
 * re-publish) is silently dropped instead of double-pausing promotions.
 *
 * <p>Pedagogy: in choreography there is no orchestrator to remember "I
 * already did this step", so each consumer maintains its own dedup table.
 * The orchestration sibling solves the same problem by persisting saga state
 * and short-circuiting on the saga-state row instead.
 */
@Entity
@Table(name = "promotion_consumed_replenishment_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumedReplenishmentEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;
}
