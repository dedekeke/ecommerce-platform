package com.ecommerce.orderservice.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Order-service view of the payment event published by payment-service on the
 * {@code payment.completed} / {@code payment.failed} topics (via its outbox).
 * Only the fields the order state machine needs are modelled; everything else
 * (amount, currency, timestamps, ...) is ignored so the contract can evolve
 * without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEventEnvelope(
    String eventType,
    String orderId,
    String paymentIntentId,
    String status,
    String failureReason
) {
}
