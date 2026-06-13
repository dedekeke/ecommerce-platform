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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
    private ArgumentCaptor<List<CurrencyRate>> ratesCaptor;

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
        verify(currencyRateRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("should_fetchAndUpsert_when_featureFlagEnabled")
    void should_fetchAndUpsert_when_featureFlagEnabled() {
        when(featureFlags.isEnabled(CurrencyRateRefreshJob.FLAG_CURRENCY_RATE_REFRESH)).thenReturn(true);
        when(rateProvider.fetchLatestRates()).thenReturn(rates("EUR", "0.93"));
        when(currencyRateRepository.findAll()).thenReturn(List.of());

        job.refresh();

        verify(rateProvider).fetchLatestRates();
        verify(currencyRateRepository).saveAll(ratesCaptor.capture());
        assertThat(ratesCaptor.getValue()).singleElement().satisfies(rate -> {
            assertThat(rate.getCode()).isEqualTo("EUR");
            assertThat(rate.getRateToUsd()).isEqualByComparingTo("0.93");
        });
    }

    @Test
    @DisplayName("should_updateExistingRate_inPlace_when_codeAlreadyPresent")
    void should_updateExistingRate_inPlace_when_codeAlreadyPresent() {
        CurrencyRate existing = CurrencyRate.builder().code("GBP").rateToUsd(new BigDecimal("0.79")).build();
        when(rateProvider.fetchLatestRates()).thenReturn(rates("GBP", "0.81"));
        when(currencyRateRepository.findAll()).thenReturn(List.of(existing));

        int upserted = job.refreshRates();

        assertThat(upserted).isOne();
        verify(currencyRateRepository).saveAll(ratesCaptor.capture());
        // Same managed entity, only the rate mutated — preserves @UpdateTimestamp semantics.
        assertThat(ratesCaptor.getValue()).singleElement().isSameAs(existing);
        assertThat(existing.getRateToUsd()).isEqualByComparingTo("0.81");
    }

    @Test
    @DisplayName("should_loadTableOnce_andUpsertEveryReturnedRate_when_multipleRates")
    void should_loadTableOnce_andUpsertEveryReturnedRate_when_multipleRates() {
        Map<String, BigDecimal> latest = new LinkedHashMap<>();
        latest.put("USD", new BigDecimal("1"));
        latest.put("EUR", new BigDecimal("0.92"));
        latest.put("JPY", new BigDecimal("149.5"));
        when(rateProvider.fetchLatestRates()).thenReturn(latest);
        when(currencyRateRepository.findAll()).thenReturn(List.of());

        int upserted = job.refreshRates();

        assertThat(upserted).isEqualTo(3);
        // Single SELECT (no N+1) and a single batched write.
        verify(currencyRateRepository).findAll();
        verify(currencyRateRepository).saveAll(ratesCaptor.capture());
        assertThat(ratesCaptor.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("should_noOpAndPreserveRates_when_providerReturnsEmpty")
    void should_noOpAndPreserveRates_when_providerReturnsEmpty() {
        when(rateProvider.fetchLatestRates()).thenReturn(Map.of());

        int upserted = job.refreshRates();

        assertThat(upserted).isZero();
        verify(currencyRateRepository, never()).findAll();
        verify(currencyRateRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("should_swallowException_when_refreshThrows")
    void should_swallowException_when_refreshThrows() {
        when(featureFlags.isEnabled(CurrencyRateRefreshJob.FLAG_CURRENCY_RATE_REFRESH)).thenReturn(true);
        when(rateProvider.fetchLatestRates()).thenThrow(new RuntimeException("provider boom"));

        // refresh() is the scheduler entry point — it must never propagate.
        job.refresh();

        verify(currencyRateRepository, never()).saveAll(anyList());
    }

    private static Map<String, BigDecimal> rates(String code, String rate) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put(code, new BigDecimal(rate));
        return map;
    }
}
