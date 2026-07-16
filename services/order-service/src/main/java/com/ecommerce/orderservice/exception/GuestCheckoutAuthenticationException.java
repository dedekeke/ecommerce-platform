package com.ecommerce.orderservice.exception;

/**
 * Thrown when a request to the unauthenticated guest checkout endpoint
 * ({@code POST /api/orders/guest}) carries an {@code Authorization} credential.
 *
 * <p>An authenticated caller must use the authenticated create ({@code POST
 * /api/orders}) so the order is owned by their real identity and counts against
 * their per-user quota. Routing a logged-in user through the guest path would
 * instead create a guest-owned order (identity derived from the email, divorced
 * from their account) and consume the looser per-IP guest rate limit. We reject
 * it as a 400 client error rather than silently accepting the ambiguity.</p>
 */
public class GuestCheckoutAuthenticationException extends RuntimeException {

    public GuestCheckoutAuthenticationException(String message) {
        super(message);
    }
}
