package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.Wishlist;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for a single wishlist entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WishlistItemResponse {

    private Long id;
    private String productId;
    private Instant addedAt;

    public static WishlistItemResponse from(Wishlist wishlist) {
        return WishlistItemResponse.builder()
                .id(wishlist.getId())
                .productId(wishlist.getProductId())
                .addedAt(wishlist.getAddedAt())
                .build();
    }
}
