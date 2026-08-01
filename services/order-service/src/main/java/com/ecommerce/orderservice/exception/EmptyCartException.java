package com.ecommerce.orderservice.exception;

/**
 * Thrown when checkout is attempted with an empty cart. This is a non-transient
 * client condition, so it maps to HTTP 400 — retrying (axios-retry only replays
 * 5xx) would never succeed, so it must not be a retryable 5xx.
 */
public class EmptyCartException extends RuntimeException {
    public EmptyCartException(String message) {
        super(message);
    }
}
