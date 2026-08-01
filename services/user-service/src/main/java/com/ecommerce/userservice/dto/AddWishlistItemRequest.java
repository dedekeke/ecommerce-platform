package com.ecommerce.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/wishlist/{userId}/items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddWishlistItemRequest {

    @NotBlank(message = "productId is required")
    @Size(max = 100, message = "productId must not exceed 100 characters")
    private String productId;
}
