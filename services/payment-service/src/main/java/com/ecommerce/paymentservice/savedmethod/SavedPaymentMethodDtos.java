package com.ecommerce.paymentservice.savedmethod;

import jakarta.validation.constraints.NotBlank;

/**
 * REST DTOs for §3.9 saved payment method endpoints.
 */
public final class SavedPaymentMethodDtos {

    private SavedPaymentMethodDtos() {}

    public record AttachRequest(@NotBlank String token) {}

    /** Response of {@code POST /setup-intent}: the browser-facing client secret for Stripe Elements. */
    public record SetupIntentResponse(String setupIntentId, String clientSecret) {}

    /** Body of {@code POST /confirm}: the SetupIntent the browser just completed with the provider. */
    public record ConfirmRequest(@NotBlank String setupIntentId) {}
}
