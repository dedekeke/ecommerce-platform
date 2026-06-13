package com.ecommerce.promotionservice.currency;

import com.ecommerce.common.featureflag.FeatureFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrencyRateRefreshJob} (§3.5).
 *
 * <p>Covers the three behaviours that matter: the feature-flag gate, the
 * idempotent upsert (insert new + update existing), and graceful degradation
 * when the provider returns nothing or the scan throws.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyRateRefreshJob")
class CurrencyRateRefreshJobTest {

    @Mock
    private CurrencyRateProvider rateProvider;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private FeatureFlags featureFlags;

    private CurrencyRateRefreshJob job;

    @Captor
    private ArgumentCaptor<CurrencyRate> rateCaptor;

    @BeforeEach
    void setUp() {
        job = new CurrencyRateRefreshJob(rateProvider, currencyRateRepository, featureFlags);
    }

    @Test
    @DisplayName("should_doNothing_when_featureFlagDisabled")
    void should_doNothing_when_featureFlagDisabled() {
        when(featureFlags.isEnabled(CurrencyRateRefreshJob.FLAG_CURRENCY_RATE_REFRESH)).thenReturn(false);

        job.refresh();

        verifyNoInteractions(rateProvider);
        verify(currencyRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_fetchAndUpsert_when_featureFlagEnabled")
    void should_fetchAndUpsert_when_featureFlagEnabled() {
        when(featureFlags.isEnabled(CurrencyRateRefreshJob.FLAG_CURRENCY_RATE_REFRESH)).thenReturn(true);
        when(rateProvider.fetchLatestRates()).thenReturn(rates("EUR", "0.93"));
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.empty());

        job.refresh();

        verify(rateProvider).fetchLatestRates();
        verify(currencyRateRepository).save(rateCaptor.capture());
        assertThat(rateCaptor.getValue().getCode()).isEqualTo("EUR");
        assertThat(rateCaptor.getValue().getRateToUsd()).isEqualByComparingTo("0.93");
    }

    @Test
    @DisplayName("should_updateExistingRate_inPlace_when_codeAlreadyPresent")
    void should_updateExistingRate_inPlace_when_codeAlreadyPresent() {
        CurrencyRate existing = CurrencyRate.builder().code("GBP").rateToUsd(new BigDecimal("0.79")).build();
        when(rateProvider.fetchLatestRates()).thenReturn(rates("GBP", "0.81"));
        when(currencyRateRepository.findById("GBP")).thenReturn(Optional.of(existing));

        int upserted = job.refreshRates();

        assertThat(upserted).isOne();
        verify(currencyRateRepository).save(rateCaptor.capture());
        // Same managed entity, only the rate mutated — preserves @UpdateTimestamp semantics.
        assertThat(rateCaptor.getValue()).isSameAs(existing);
        assertThat(rateCaptor.getValue().getRateToUsd()).isEqualByComparingTo("0.81");
    }

    @Test
    @DisplayName("should_upsertEveryReturnedRate_when_multipleRates")
    void should_upsertEveryReturnedRate_when_multipleRates() {
        Map<String, BigDecimal> latest = new LinkedHashMap<>();
        latest.put("USD", new BigDecimal("1"));
        latest.put("EUR", new BigDecimal("0.92"));
        latest.put("JPY", new BigDecimal("149.5"));
        when(rateProvider.fetchLatestRates()).thenReturn(latest);
        when(currencyRateRepository.findById(any())).thenReturn(Optional.empty());

        int upserted = job.refreshRates();

        assertThat(upserted).isEqualTo(3);
        verify(currencyRateRepository, times(3)).save(any(CurrencyRate.class));
    }

    @Test
    @DisplayName("should_noOpAndPreserveRates_when_providerReturnsEmpty")
    void should_noOpAndPreserveRates_when_providerReturnsEmpty() {
        when(rateProvider.fetchLatestRates()).thenReturn(Map.of());

        int upserted = job.refreshRates();

        assertThat(upserted).isZero();
        verify(currencyRateRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_swallowException_when_refreshThrows")
    void should_swallowException_when_refreshThrows() {
        when(featureFlags.isEnabled(CurrencyRateRefreshJob.FLAG_CURRENCY_RATE_REFRESH)).thenReturn(true);
        when(rateProvider.fetchLatestRates()).thenThrow(new RuntimeException("provider boom"));

        // refresh() is the scheduler entry point — it must never propagate.
        job.refresh();

        verify(currencyRateRepository, never()).save(any());
    }

    private static Map<String, BigDecimal> rates(String code, String rate) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put(code, new BigDecimal(rate));
        return map;
    }
}
