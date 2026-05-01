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
}
