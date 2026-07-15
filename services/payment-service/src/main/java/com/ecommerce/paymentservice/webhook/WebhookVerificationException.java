package com.ecommerce.paymentservice.webhook;

/**
 * Raised when a Stripe webhook cannot be authenticated — a missing/malformed
 * {@code Stripe-Signature} header, a signature that does not match the payload
 * under the configured signing secret, or a timestamp outside Stripe's
 * tolerance window. Signature verification is the ONLY authentication for the
 * webhook endpoint (it is exempt from JWT auth because Stripe cannot present a
 * JWT), so a failure here must reject the request with 400 and apply no state
 * change.
 */
public class WebhookVerificationException extends RuntimeException {

    public WebhookVerificationException(String message) {
        super(message);
    }

    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
