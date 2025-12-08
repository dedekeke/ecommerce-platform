package com.ecommerce.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cart item response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private String id;
    private String productId;
    private String productName;
    private String productSku;
    private String productImageUrl;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    private Instant addedAt;
    private Instant updatedAt;
}
