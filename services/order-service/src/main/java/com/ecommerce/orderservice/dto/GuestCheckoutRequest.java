package com.ecommerce.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/orders/guest} — the unauthenticated checkout path.
 *
 * <p>Unlike the authenticated {@link CheckoutRequest} there is no {@code userId}:
 * the owning identity is derived server-side from {@code email} (see
 * {@code GuestIdentityFactory}), so a client can never assert who it is. The cart
 * itself is not sent; the order-creation saga reads the guest's cart server-side,
 * keyed by that derived identity.</p>
 *
 * <p>{@code email} is REQUIRED and format-validated (mirrored by a gateway-level
 * check): it is both the guest-identity seed and the claim key persisted on the
 * order for later account linking. {@code userName} is the optional recipient
 * name used for the confirmation email.</p>
 */
public record GuestCheckoutRequest(
    @NotBlank(message = "Email is required for guest checkout")
    @Email(message = "email must be a valid email address")
    String email,
    @NotNull(message = "Shipping address is required") @Valid AddressDto shippingAddress,
    String promotionCode,
    String userName
) {
}
