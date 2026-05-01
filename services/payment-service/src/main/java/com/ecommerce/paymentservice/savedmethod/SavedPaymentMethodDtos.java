package com.ecommerce.paymentservice.savedmethod;

import jakarta.validation.constraints.NotBlank;

/**
 * REST DTOs for §3.9 saved payment method endpoints.
 */
public final class SavedPaymentMethodDtos {

    private SavedPaymentMethodDtos() {}

    public record AttachRequest(@NotBlank String token) {}
}
