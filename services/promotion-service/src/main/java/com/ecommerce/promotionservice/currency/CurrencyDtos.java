package com.ecommerce.promotionservice.currency;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request and response payloads for the currency endpoints (§3.5).
 */
public final class CurrencyDtos {

    private CurrencyDtos() {}

    public record ConvertRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String from,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String to
    ) {}

    public record ConvertResponse(BigDecimal amount, BigDecimal rate) {}

    public record RatesResponse(String base, Map<String, BigDecimal> rates) {}
}
