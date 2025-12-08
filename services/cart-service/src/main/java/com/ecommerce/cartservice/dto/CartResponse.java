package com.ecommerce.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cart response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String id;
    private String userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
