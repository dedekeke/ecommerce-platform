package com.ecommerce.promotionservice.currency;

import com.ecommerce.common.featureflag.FeatureFlags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Periodically refreshes the {@code currency_rates} pivot table from an
 * external FX provider (§3.5).
 *
 * <p>The table is seeded once by Flyway (V4) and was previously refreshed
 * manually by ops. This job pulls fresh rates on a configurable cadence
 * ({@code currency.rate.refresh-cron}, default hourly) and upserts them.
 *
 * <p><strong>Gated behind a feature flag.</strong> The job is a no-op unless
 * {@code FEATURE_FLAG_CURRENCY_RATE_REFRESH=true} (or
 * {@code feature.flag.currency-rate-refresh=true}). It defaults OFF so the
 * platform keeps the deterministic seeded rates until ops explicitly opts in
 * to live refresh.
 *
 * <p><strong>Idempotent.</strong> Rates are upserted by primary key (the ISO
 * code), so a re-run with identical provider data converges to the same table
 * state. An empty provider response is treated as a no-op — we never wipe the
 * last known rates on a transient outage.
 */
@Slf4j
@Component
public class CurrencyRateRefreshJob {

    static final String FLAG_CURRENCY_RATE_REFRESH = "CURRENCY_RATE_REFRESH";

    private final CurrencyRateProvider rateProvider;
    private final CurrencyRateRepository currencyRateRepository;
    private final FeatureFlags featureFlags;

    public CurrencyRateRefreshJob(
            CurrencyRateProvider rateProvider,
            CurrencyRateRepository currencyRateRepository,
            FeatureFlags featureFlags) {
        this.rateProvider = rateProvider;
        this.currencyRateRepository = currencyRateRepository;
        this.featureFlags = featureFlags;
    }

    @Scheduled(cron = "${currency.rate.refresh-cron:0 0 * * * *}")
    public void refresh() {
        if (!featureFlags.isEnabled(FLAG_CURRENCY_RATE_REFRESH)) {
            log.debug("Currency rate refresh flag disabled; skipping");
            return;
        }
        try {
            int upserted = refreshRates();
            log.info("Currency rate refresh complete; upserted {} rate(s)", upserted);
        } catch (Exception ex) {
            log.error("Currency rate refresh failed", ex);
        }
    }

    /**
     * Visible for testing — fetches and upserts the latest rates, returning
     * the number of rows written. Runs in a single transaction so a failure
     * mid-batch rolls back cleanly.
     */
    @Transactional
    public int refreshRates() {
        Map<String, BigDecimal> latest = rateProvider.fetchLatestRates();
        if (latest.isEmpty()) {
            log.warn("No rates returned by provider; leaving current rates untouched");
            return 0;
        }

        int upserted = 0;
        for (Map.Entry<String, BigDecimal> entry : latest.entrySet()) {
            CurrencyRate rate = currencyRateRepository.findById(entry.getKey())
                    .orElseGet(() -> CurrencyRate.builder().code(entry.getKey()).build());
            rate.setRateToUsd(entry.getValue());
            currencyRateRepository.save(rate);
            upserted++;
        }
        return upserted;
    }
}
