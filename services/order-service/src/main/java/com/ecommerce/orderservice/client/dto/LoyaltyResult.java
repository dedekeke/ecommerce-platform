package com.ecommerce.orderservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Subset of promotion-service's loyalty response that order-service consumes
 * when pricing a checkout. Only {@code tier} and {@code discountPercent} are
 * relevant here; the rest of the response (next-tier hints) is ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoyaltyResult {

    private String tier;

    /** Tier discount percentage, 0..100. */
    private BigDecimal discountPercent;
}
