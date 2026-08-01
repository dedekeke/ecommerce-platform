package com.ecommerce.orderservice.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request and response DTOs for the subscription REST API. Kept in one file
 * because they are tiny and tightly coupled to the controller's contract.
 */
public class SubscriptionDtos {

    private SubscriptionDtos() {}

    public record CreateSubscriptionRequest(
        @NotBlank String userId,
        @NotBlank String productId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @Min(1) Integer intervalDays,
        String paymentMethodId,
        @NotBlank String shippingAddressJson
    ) {}
}
