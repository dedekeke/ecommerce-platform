package com.ecommerce.promotionservice.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Static FX-rate row keyed by ISO 4217 code (§3.5).
 *
 * <p>{@code rateToUsd} is the multiplier from USD to this currency:
 * {@code amount_in_currency = amount_in_usd * rateToUsd}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "currency_rates")
public class CurrencyRate {

    @Id
    @Column(length = 3, nullable = false)
    private String code;

    @Column(name = "rate_to_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal rateToUsd;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
