package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemDto(
        String productId,
        Integer quantity,
        BigDecimal unitPrice
) {
}
