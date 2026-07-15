package com.ecommerce.paymentservice.webhook;

import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single Stripe webhook delivery: authenticate → reconcile.
 *
 * <p>Deliberately NOT {@code @Transactional} so it can catch a duplicate-key
 * violation <em>after</em> {@link StripeWebhookProcessor}'s transaction has
 * rolled back (a self-invoked transactional method would bypass the proxy, and
 * catching inside the transaction hits the "marked rollback-only" trap). The
 * signature check runs first and is the sole authentication for this
 * JWT-exempt endpoint.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final StripeWebhookVerifier verifier;
    private final StripeWebhookProcessor processor;

    /**
     * @throws WebhookVerificationException if the signature is missing/invalid;
     *         the API layer maps this to 400 and no state changes.
     */
    public void handle(String rawPayload, String signatureHeader) {
        Event event = verifier.verifyAndParse(rawPayload, signatureHeader);
        try {
            processor.process(event);
        } catch (DataIntegrityViolationException duplicate) {
            // Insert-first dedup: this exact Stripe event id was already applied.
            // Stripe delivers at-least-once, so a redelivery converges to success.
            log.info("Duplicate Stripe webhook event {} ignored (already processed)", event.getId());
        }
    }
}
