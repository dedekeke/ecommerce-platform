package com.ecommerce.orderservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Minimal projection of product-service's {@code ProductResponse} — the
 * subscription scheduler only needs the current price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPriceResponse {

    private String id;
    private BigDecimal price;
}
