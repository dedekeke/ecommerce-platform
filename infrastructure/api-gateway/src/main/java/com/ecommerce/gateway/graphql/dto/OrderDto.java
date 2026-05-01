package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        String id,
        String orderNumber,
        String userId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemDto> items,
        String createdAt
) {
    public List<OrderItemDto> safeItems() {
        return items == null ? List.of() : items;
    }
}
