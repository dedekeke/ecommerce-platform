package com.ecommerce.promotionservice.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CurrencyService} (§3.5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyService")
class CurrencyServiceTest {

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @InjectMocks
    private CurrencyService currencyService;

    private CurrencyRate usd;
    private CurrencyRate eur;
    private CurrencyRate jpy;

    @BeforeEach
    void setUp() {
        usd = rate("USD", "1");
        eur = rate("EUR", "0.92");
        jpy = rate("JPY", "149.5");
    }

    @Test
    @DisplayName("listRates_should_returnAllSeededRates")
    void listRates_should_returnAllSeededRates() {
        when(currencyRateRepository.findAll()).thenReturn(List.of(usd, eur, jpy));

        CurrencyDtos.RatesResponse response = currencyService.listRates();

        assertThat(response.base()).isEqualTo("USD");
        assertThat(response.rates()).containsKeys("USD", "EUR", "JPY");
        assertThat(response.rates().get("EUR")).isEqualByComparingTo("0.92");
    }

    @Test
    @DisplayName("convert_should_returnIdentity_when_fromAndToMatch")
    void convert_should_returnIdentity_when_fromAndToMatch() {
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.of(eur));

        CurrencyDtos.ConvertResponse response = currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("100"), "EUR", "EUR"));

        assertThat(response.amount()).isEqualByComparingTo("100");
        assertThat(response.rate()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("convert_should_convertUsdToEur_using0_92Multiplier")
    void convert_should_convertUsdToEur_using0_92Multiplier() {
        when(currencyRateRepository.findById("USD")).thenReturn(Optional.of(usd));
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.of(eur));

        CurrencyDtos.ConvertResponse response = currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("100"), "USD", "EUR"));

        assertThat(response.rate()).isEqualByComparingTo("0.92");
        assertThat(response.amount()).isEqualByComparingTo("92.0000");
    }

    @Test
    @DisplayName("convert_should_handleEurToJpy_throughUsdPivot")
    void convert_should_handleEurToJpy_throughUsdPivot() {
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.of(eur));
        when(currencyRateRepository.findById("JPY")).thenReturn(Optional.of(jpy));

        CurrencyDtos.ConvertResponse response = currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("10"), "EUR", "JPY"));

        // 10 EUR -> 10 / 0.92 USD -> (10/0.92) * 149.5 JPY ≈ 1625.00 JPY
        assertThat(response.amount()).isEqualByComparingTo("1625.0000");
    }

    @Test
    @DisplayName("convert_should_normalizeLowercaseCodes")
    void convert_should_normalizeLowercaseCodes() {
        when(currencyRateRepository.findById("USD")).thenReturn(Optional.of(usd));
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.of(eur));

        CurrencyDtos.ConvertResponse response = currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("100"), "usd", "eur"));

        assertThat(response.amount()).isEqualByComparingTo("92.0000");
    }

    @Test
    @DisplayName("convert_should_throw_when_fromCurrencyMissing")
    void convert_should_throw_when_fromCurrencyMissing() {
        when(currencyRateRepository.findById("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("1"), "XYZ", "USD")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("XYZ");
    }

    @Test
    @DisplayName("convert_should_throw_when_toCurrencyMissing")
    void convert_should_throw_when_toCurrencyMissing() {
        when(currencyRateRepository.findById("USD")).thenReturn(Optional.of(usd));
        when(currencyRateRepository.findById("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.convert(
            new CurrencyDtos.ConvertRequest(new BigDecimal("1"), "USD", "XYZ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("convert_should_treatNullAmountAsZero")
    void convert_should_treatNullAmountAsZero() {
        when(currencyRateRepository.findById("USD")).thenReturn(Optional.of(usd));
        when(currencyRateRepository.findById("EUR")).thenReturn(Optional.of(eur));

        CurrencyDtos.ConvertResponse response = currencyService.convert(
            new CurrencyDtos.ConvertRequest(null, "USD", "EUR"));

        assertThat(response.amount()).isEqualByComparingTo("0.0000");
    }

    private CurrencyRate rate(String code, String rate) {
        return CurrencyRate.builder()
            .code(code)
            .rateToUsd(new BigDecimal(rate))
            .build();
    }
}
