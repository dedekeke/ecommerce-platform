package com.ecommerce.paymentservice.webhook;

import com.ecommerce.paymentservice.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Real Stripe signature verification, backed by {@link Webhook#constructEvent}.
 *
 * <p>{@code constructEvent} recomputes the HMAC-SHA256 of {@code timestamp.payload}
 * with the webhook signing secret and compares it (constant-time) against the
 * {@code v1} scheme in the {@code Stripe-Signature} header, also rejecting
 * timestamps outside the default tolerance (replay protection). The signing
 * secret comes from {@code STRIPE_WEBHOOK_SECRET} — it is never hardcoded or
 * committed. Any verification failure is normalised to
 * {@link WebhookVerificationException} so the API layer can answer 400 without
 * leaking Stripe internals.
 */
@Component
@Slf4j
public class DefaultStripeWebhookVerifier implements StripeWebhookVerifier {

    private final StripeProperties properties;

    public DefaultStripeWebhookVerifier(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Event verifyAndParse(String rawPayload, String signatureHeader) {
        if (!StringUtils.hasText(signatureHeader)) {
            throw new WebhookVerificationException("Missing Stripe-Signature header");
        }
        String secret = properties.getWebhookSecret();
        if (!StringUtils.hasText(secret)) {
            // Fail closed: without a configured secret we cannot authenticate the
            // caller, so we must never treat the payload as trusted.
            throw new WebhookVerificationException("Webhook signing secret is not configured");
        }
        try {
            return Webhook.constructEvent(rawPayload, signatureHeader, secret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            throw new WebhookVerificationException("Invalid Stripe webhook signature", e);
        }
    }
}
