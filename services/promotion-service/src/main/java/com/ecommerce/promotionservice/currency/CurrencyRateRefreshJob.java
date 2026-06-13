package com.ecommerce.promotionservice.currency;

import com.ecommerce.common.featureflag.FeatureFlags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        // Single SELECT for the whole table instead of one findById per code
        // (the currency_rates table is small and bounded by ISO codes). Existing
        // managed entities are mutated in place to preserve @UpdateTimestamp
        // semantics; unknown codes are inserted.
        Map<String, CurrencyRate> existingByCode = currencyRateRepository.findAll().stream()
                .collect(Collectors.toMap(CurrencyRate::getCode, Function.identity(), (a, b) -> a, HashMap::new));

        List<CurrencyRate> toSave = new ArrayList<>(latest.size());
        latest.forEach((code, rate) -> {
            CurrencyRate entity = existingByCode.getOrDefault(code, CurrencyRate.builder().code(code).build());
            entity.setRateToUsd(rate);
            toSave.add(entity);
        });

        currencyRateRepository.saveAll(toSave);
        return toSave.size();
    }
}
