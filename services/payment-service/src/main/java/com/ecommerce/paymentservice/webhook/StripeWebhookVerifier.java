package com.ecommerce.paymentservice.webhook;

import com.stripe.model.Event;

/**
 * Boundary that authenticates and parses a raw Stripe webhook payload.
 *
 * <p>Extracted behind an interface so the webhook handler logic can be unit
 * tested with a stubbed verifier, while a single real implementation
 * ({@link DefaultStripeWebhookVerifier}) owns the Stripe SDK signature check.
 */
public interface StripeWebhookVerifier {

    /**
     * Verify the {@code Stripe-Signature} header against the raw request body
     * using the configured webhook signing secret, and parse the body into a
     * Stripe {@link Event}.
     *
     * @param rawPayload      the EXACT bytes Stripe sent, decoded as UTF-8 —
     *                        never a re-serialized body, or the HMAC will not match
     * @param signatureHeader the {@code Stripe-Signature} header value
     * @return the verified event
     * @throws WebhookVerificationException if the signature is missing, malformed,
     *                                      or does not match
     */
    Event verifyAndParse(String rawPayload, String signatureHeader);
}
