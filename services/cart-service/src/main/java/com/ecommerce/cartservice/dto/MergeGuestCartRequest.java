package com.ecommerce.cartservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of the authenticated {@code POST /api/cart/merge} claim endpoint.
 *
 * <p>On login the browser sends the email the shopper used while browsing as a
 * guest. cart-service re-derives the same {@code guest:sha256(email)} identity
 * (see {@code GuestIdentityFactory}), finds that guest cart, and folds its items
 * into the authenticated user's cart. The email is the claim key, exactly as it
 * is for the guest-order claim seam in order-service.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MergeGuestCartRequest {

    @NotBlank(message = "Guest email is required")
    @Email(message = "Guest email must be a valid email address")
    private String guestEmail;
}
