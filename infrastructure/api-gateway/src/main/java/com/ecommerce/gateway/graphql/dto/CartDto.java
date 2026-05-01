package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CartDto(
        String userId,
        List<CartItemDto> items,
        BigDecimal totalAmount,
        Integer totalItems,
        List<PromotionDto> appliedPromotions
) {
    public List<CartItemDto> safeItems() {
        return items == null ? List.of() : items;
    }

    public List<PromotionDto> safePromotions() {
        return appliedPromotions == null ? List.of() : appliedPromotions;
    }
}
