package com.ecommerce.orderservice.saga.rma;

/**
 * Domain-level error for RMA saga validation failures (e.g. order not
 * eligible, invalid status transition). Translates to HTTP 400 in the
 * controller. Distinct from infrastructure errors (network, DB) which
 * propagate as 500.
 */
public class RmaException extends RuntimeException {

    public RmaException(String message) {
        super(message);
    }

    public RmaException(String message, Throwable cause) {
        super(message, cause);
    }
}
