package com.ecommerce.paymentservice.webhook;

import com.ecommerce.paymentservice.savedmethod.SavedPaymentMethodService;
import com.ecommerce.paymentservice.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Transactional worker behind {@link PaymentWebhookService}.
 *
 * <p>Lives in its own bean so the whole unit runs through the Spring transaction
 * proxy and so the orchestrator can catch a rolled-back duplicate-key violation
 * <em>after</em> the transaction has fully rolled back — the same shape as the
 * loyalty dedup ({@code OrderCompletedProcessor}) from PR#119.
 *
 * <p>Insert-first idempotency: claim the Stripe event by INSERTing its
 * {@link ProcessedStripeEvent} row, then reconcile the payment + emit the
 * settlement event, all in one transaction. A duplicate event id fails the
 * INSERT ({@code DataIntegrityViolationException}) before any state change, and
 * the transaction rolls back — so a Stripe redelivery or a concurrent replica
 * can never emit the settlement twice.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StripeWebhookProcessor {

    static final String EVENT_SUCCEEDED = "payment_intent.succeeded";
    static final String EVENT_FAILED = "payment_intent.payment_failed";
    static final String EVENT_CANCELED = "payment_intent.canceled";
    static final String EVENT_SETUP_SUCCEEDED = "setup_intent.succeeded";

    private final ProcessedStripeEventRepository processedEventRepository;
    private final PaymentService paymentService;
    private final SavedPaymentMethodService savedPaymentMethodService;

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException if the
     *         event id was already processed — the caller treats this as an
     *         idempotent success.
     */
    @Transactional
    public void process(Event event) {
        String type = event.getType();
        if (!isHandled(type)) {
            log.debug("Ignoring unhandled Stripe event type {} (id {})", type, event.getId());
            return;
        }

        // Insert-first: fails here on a duplicate, before any state is touched.
        processedEventRepository.saveAndFlush(ProcessedStripeEvent.builder()
                .eventId(event.getId())
                .eventType(type)
                .processedAt(Instant.now())
                .build());

        if (EVENT_SETUP_SUCCEEDED.equals(type)) {
            handleSetupIntentSucceeded(event);
            return;
        }

        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null || intent.getId() == null) {
            log.warn("Stripe event {} ({}) carried no deserializable PaymentIntent; acknowledging",
                    event.getId(), type);
            return;
        }

        switch (type) {
            case EVENT_SUCCEEDED -> paymentService.markPaymentSucceeded(intent.getId(), intent.getLatestCharge());
            case EVENT_FAILED -> paymentService.markPaymentFailed(intent.getId(), failureReason(intent));
            case EVENT_CANCELED -> paymentService.markPaymentFailed(intent.getId(), "Payment canceled");
            default -> { /* unreachable: guarded by isHandled */ }
        }
    }

    private boolean isHandled(String type) {
        return EVENT_SUCCEEDED.equals(type) || EVENT_FAILED.equals(type)
                || EVENT_CANCELED.equals(type) || EVENT_SETUP_SUCCEEDED.equals(type);
    }

    /**
     * Authoritatively persist a saved card once its SetupIntent succeeds. The owning
     * user is read from the intent metadata (stamped at creation) — never from a
     * client — and persistence is idempotent, so a redelivery is a no-op.
     */
    private void handleSetupIntentSucceeded(Event event) {
        SetupIntent setupIntent = extractSetupIntent(event);
        if (setupIntent == null) {
            log.warn("Stripe event {} carried no deserializable SetupIntent; acknowledging", event.getId());
            return;
        }
        String userId = setupIntent.getMetadata() == null ? null : setupIntent.getMetadata().get("userId");
        String paymentMethodId = setupIntent.getPaymentMethod();
        if (userId == null || userId.isBlank() || paymentMethodId == null || paymentMethodId.isBlank()) {
            log.warn("SetupIntent {} missing userId metadata or payment method; skipping persist",
                    setupIntent.getId());
            return;
        }
        savedPaymentMethodService.persistFromWebhook(userId, paymentMethodId);
    }

    private SetupIntent extractSetupIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject().orElse(null);
        if (object == null) {
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.warn("Could not deserialize SetupIntent from event {}: {}", event.getId(), e.getMessage());
                return null;
            }
        }
        return (object instanceof SetupIntent setupIntent) ? setupIntent : null;
    }

    /**
     * Extract the PaymentIntent from the event payload. Prefers the SDK-version
     * matched object; falls back to an unsafe deserialize when Stripe sent a
     * different API version than the SDK expects, rather than dropping the event.
     */
    private PaymentIntent extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject().orElse(null);
        if (object == null) {
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.warn("Could not deserialize PaymentIntent from event {}: {}", event.getId(), e.getMessage());
                return null;
            }
        }
        return (object instanceof PaymentIntent intent) ? intent : null;
    }

    private String failureReason(PaymentIntent intent) {
        StripeError error = intent.getLastPaymentError();
        if (error != null && error.getMessage() != null) {
            return error.getMessage();
        }
        return "Payment failed";
    }
}
