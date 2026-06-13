package com.ecommerce.promotionservice.currency;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fetches the latest USD-pivot FX rates from an external provider.
 *
 * <p>Returned map is keyed by ISO 4217 code and valued by
 * {@code rateToUsd = unitsOfCurrencyPerOneUsd} — the same convention the
 * {@code currency_rates} table and {@link CurrencyService} use. USD itself
 * is expected to be {@code 1}.
 */
public interface CurrencyRateProvider {

    /**
     * @return latest rates keyed by upper-case ISO code; never {@code null}.
     *         An empty map signals "nothing to upsert" (treated as a no-op
     *         by the refresh job rather than wiping the table).
     */
    Map<String, BigDecimal> fetchLatestRates();
}
