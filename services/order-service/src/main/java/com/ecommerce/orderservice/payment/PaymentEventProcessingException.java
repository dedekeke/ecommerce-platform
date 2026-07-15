package com.ecommerce.orderservice.payment;

/**
 * Thrown for a payment event that can never succeed on retry — a malformed
 * (non-JSON) payload or one missing the {@code orderId}. It is classified
 * non-retryable by the consumer's error handler so the record is dead-lettered
 * immediately rather than looping through the backoff or being silently dropped.
 */
public class PaymentEventProcessingException extends RuntimeException {

    public PaymentEventProcessingException(String message) {
        super(message);
    }

    public PaymentEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
