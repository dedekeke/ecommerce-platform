package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Response body for {@code GET /api/promotions/loyalty/{userId}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoyaltyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Tier name, e.g. {@code "GOLD"}. Never null — defaults to BRONZE. */
    private String tier;

    /** Tier discount percentage, 0..100. */
    private BigDecimal discountPercent;

    /** Lifetime spend accumulated by this service. */
    private BigDecimal currentSpend;

    /**
     * Spend amount required to reach the next tier, or {@code null} when the
     * customer is already on the highest tier.
     */
    private BigDecimal nextTierAt;

    /** Name of the next tier, or {@code null} when on the highest tier. */
    private String nextTier;
}
