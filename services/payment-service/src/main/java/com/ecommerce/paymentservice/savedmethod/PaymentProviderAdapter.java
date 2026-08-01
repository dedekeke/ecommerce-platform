package com.ecommerce.paymentservice.savedmethod;

/**
 * Abstraction over the upstream tokenization provider for §3.9.
 *
 * <p>The current production binding is {@link MockStripeAdapter}. Swapping in
 * the real Stripe SDK is one diff: drop a {@code StripePaymentProviderAdapter}
 * with {@code @Profile("!mock-payment-provider")} and the existing service
 * picks it up via Spring DI without any service-layer change.</p>
 */
public interface PaymentProviderAdapter {

    /**
     * The {@code provider} value persisted on every {@link SavedPaymentMethod}
     * created through this adapter (e.g. {@code "STRIPE"}, {@code "MOCK"}).
     */
    String providerName();

    /**
     * Attach a tokenized payment method to a user. The {@code token} is what
     * the client-side SDK returned (e.g. Stripe.js {@code pm_...} id) — the
     * adapter is responsible for translating it into a stable
     * {@link AttachResult}.
     */
    AttachResult attachPaymentMethod(String userId, String token);

    /**
     * Create a provider-side SetupIntent so the browser can collect and save a
     * card via the provider's SDK (Stripe Elements) WITHOUT the raw PAN ever
     * touching our backend (PCI SAQ-A). The returned {@code clientSecret} is
     * handed to the browser; the {@code userId} is stamped onto the intent's
     * metadata so the later confirmation/webhook can be authorized against the
     * owning user.
     */
    SetupIntentResult createSetupIntent(String userId);

    /**
     * Retrieve a previously-created SetupIntent from the provider (payment
     * method expanded) so the confirm endpoint can verify it succeeded, verify
     * ownership via metadata, and read display-safe card fields. No PAN crosses
     * this boundary.
     */
    SetupIntentDetails retrieveSetupIntent(String setupIntentId);

    /**
     * Result of a successful attach call — only display-safe metadata.
     * No PAN ever crosses this boundary.
     */
    record AttachResult(
        String providerId,
        String last4,
        String brand,
        Integer expMonth,
        Integer expYear
    ) {}

    /** A freshly-created SetupIntent: its id plus the browser-facing client secret. */
    record SetupIntentResult(
        String setupIntentId,
        String clientSecret
    ) {}

    /**
     * A retrieved SetupIntent flattened to what the confirm flow needs: status,
     * the owning {@code userId} (from metadata), the resulting payment method id,
     * and display-safe card fields. No PAN.
     */
    record SetupIntentDetails(
        String setupIntentId,
        String status,
        String userId,
        String paymentMethodId,
        String last4,
        String brand,
        Integer expMonth,
        Integer expYear
    ) {}
}
