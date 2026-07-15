package com.ecommerce.paymentservice.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Idempotency ledger for the Stripe webhook handler.
 *
 * <p>Stores the Stripe {@code event.id} of every webhook already applied to a
 * {@link com.ecommerce.paymentservice.domain.Payment}. Stripe delivers webhooks
 * at-least-once and retries on any non-2xx response, so the same event id can be
 * redelivered; recording it lets the handler drop duplicates instead of
 * re-emitting a settlement event to order-service. Mirrors the loyalty consumer
 * dedup ({@code ProcessedLoyaltyEvent}) shipped in PR#119.
 *
 * <p>Implements {@link Persistable} with {@code isNew() == true} so Spring Data
 * always issues an {@code INSERT} (never a SELECT-then-merge). That makes the
 * primary key the dedup guard: a duplicate event id fails the INSERT with a
 * duplicate-key violation and rolls back the reconciliation transaction, so a
 * redelivery or a concurrent replica can never apply the same event twice.
 */
@Entity
@Table(name = "processed_stripe_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedStripeEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
