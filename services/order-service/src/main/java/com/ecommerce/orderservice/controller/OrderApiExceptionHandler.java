package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.exception.ConcurrentCheckoutException;
import com.ecommerce.orderservice.exception.EmptyCartException;
import com.ecommerce.orderservice.exception.GuestCheckoutAuthenticationException;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.UserMismatchException;
import com.ecommerce.orderservice.saga.OrderCreationSaga;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Translates order REST API security errors into structured JSON responses. */
@RestControllerAdvice(basePackages = {
    "com.ecommerce.orderservice.controller",
    "com.ecommerce.orderservice.subscription",
    "com.ecommerce.orderservice.saga"
})
@Slf4j
public class OrderApiExceptionHandler {

    @ExceptionHandler(UserMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleUserMismatch(UserMismatchException ex) {
        log.warn("Order request user mismatch: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * A JWT/Authorization credential was presented to the guest checkout path.
     * 400 (client error): the caller is authenticated and must instead use
     * {@code POST /api/orders}. Not a 401/403 — the credential itself may be
     * perfectly valid; it is simply not accepted on this endpoint.
     */
    @ExceptionHandler(GuestCheckoutAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleGuestAuth(GuestCheckoutAuthenticationException ex) {
        log.warn("Guest checkout rejected — Authorization present: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * A checkout is already in flight for the supplied Idempotency-Key. 409 (not
     * a 5xx) so the checkout-mfe's axios-retry does NOT replay it — replaying a
     * genuine concurrent double-submit is exactly what we must avoid.
     */
    @ExceptionHandler(ConcurrentCheckoutException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentCheckout(ConcurrentCheckoutException ex) {
        log.warn("Concurrent checkout rejected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /**
     * An invalid order-status transition was attempted (e.g. shipping a PENDING
     * order, or shipping an already-DELIVERED one). 409 (conflict with the
     * order's current state) — not a 5xx, since retrying the same transition can
     * never succeed until the order legitimately reaches a valid source state.
     */
    @ExceptionHandler(InvalidOrderStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(
            InvalidOrderStatusTransitionException ex) {
        log.warn("Invalid order status transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody(HttpStatus.CONFLICT, ex.getMessage()));
    }

    /**
     * Empty cart is a non-transient client condition — 400, never a retryable
     * 5xx (retrying an empty cart can never succeed).
     */
    @ExceptionHandler(EmptyCartException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyCart(EmptyCartException ex) {
        log.warn("Checkout rejected — empty cart: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * The order-creation saga failed on a cart/inventory/payment dependency.
     * 502 keeps this in the 5xx band so a transient failure is retried by the
     * client; compensation has already released any partial work (reservation +
     * speculative order) inside the saga.
     */
    @ExceptionHandler(OrderCreationSaga.SagaException.class)
    public ResponseEntity<Map<String, Object>> handleSagaFailure(OrderCreationSaga.SagaException ex) {
        log.error("Order creation saga failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(errorBody(HttpStatus.BAD_GATEWAY, ex.getMessage()));
    }

    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return body;
    }
}
