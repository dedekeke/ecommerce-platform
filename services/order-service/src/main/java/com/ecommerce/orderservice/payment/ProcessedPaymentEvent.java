package com.ecommerce.orderservice.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Consumer-side idempotency ledger for payment events.
 *
 * <p>The {@code eventId} (the producer's {@code outbox-event-id} header) is the
 * primary key, so a second delivery of the same event short-circuits the
 * existence check — an at-least-once duplicate is applied at most once. The row
 * is written in the SAME transaction as the order mutation, so "recorded as
 * processed" and "state changed" commit atomically.
 */
@Entity
@Table(name = "processed_payment_event", indexes = {
    @Index(name = "idx_processed_payment_event_order", columnList = "orderId")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedPaymentEvent {

    @Id
    @Column(nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 128)
    private String orderId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public static ProcessedPaymentEvent of(String eventId, String eventType, String orderId) {
        return new ProcessedPaymentEvent(eventId, eventType, orderId, null);
    }
}
