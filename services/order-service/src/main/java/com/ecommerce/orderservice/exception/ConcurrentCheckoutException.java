package com.ecommerce.orderservice.exception;

/**
 * Thrown when a checkout arrives with an Idempotency-Key that is still being
 * processed by an in-flight request. Surfaces as HTTP 409 — the client must not
 * blindly retry (axios-retry only replays 5xx), which keeps a genuine
 * double-submit from creating a duplicate order.
 */
public class ConcurrentCheckoutException extends RuntimeException {
    public ConcurrentCheckoutException(String message) {
        super(message);
    }
}
