package com.ecommerce.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/orders}. The cart contents are NOT sent by the
 * client — the order-creation saga fetches the authenticated user's real cart
 * over gRPC, so the client only supplies the shipping address, an optional
 * promotion code and the recipient details used for the confirmation email.
 *
 * <p>{@code userId} is an optional correlation hint only. The effective owner is
 * always the JWT subject (see {@code UserIdentityResolver}); a mismatching value
 * is rejected with 403.</p>
 */
public record CheckoutRequest(
    String userId,
    @NotNull(message = "Shipping address is required") @Valid AddressDto shippingAddress,
    String promotionCode,
    @Email(message = "userEmail must be a valid email address") String userEmail,
    String userName
) {
}
