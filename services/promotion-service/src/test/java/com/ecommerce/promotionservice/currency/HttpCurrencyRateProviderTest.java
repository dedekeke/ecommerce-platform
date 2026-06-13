package com.ecommerce.promotionservice.currency;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link HttpCurrencyRateProvider} against a WireMock stub so the
 * provider's HTTP + JSON-parsing behaviour is exercised without touching a
 * live FX API.
 */
@DisplayName("HttpCurrencyRateProvider")
class HttpCurrencyRateProviderTest {

    private WireMockServer wireMock;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("should_parseRates_when_providerReturnsUsdPivotPayload")
    void should_parseRates_when_providerReturnsUsdPivotPayload() {
        wireMock.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"base":"USD","rates":{"EUR":0.92,"GBP":0.79,"JPY":149.5}}
                                """)));

        Map<String, BigDecimal> rates = provider(baseUrl + "/latest").fetchLatestRates();

        assertThat(rates).hasSize(3);
        assertThat(rates.get("EUR")).isEqualByComparingTo("0.92");
        assertThat(rates.get("GBP")).isEqualByComparingTo("0.79");
        assertThat(rates.get("JPY")).isEqualByComparingTo("149.5");
    }

    @Test
    @DisplayName("should_normaliseCodesToUpperCase_andDropNonPositiveRates")
    void should_normaliseCodesToUpperCase_andDropNonPositiveRates() {
        wireMock.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"base":"USD","rates":{"eur":0.9,"cad":0,"vnd":-1}}
                                """)));

        Map<String, BigDecimal> rates = provider(baseUrl + "/latest").fetchLatestRates();

        assertThat(rates).containsOnlyKeys("EUR");
        assertThat(rates.get("EUR")).isEqualByComparingTo("0.9");
    }

    @Test
    @DisplayName("should_returnEmpty_when_providerReturnsServerError")
    void should_returnEmpty_when_providerReturnsServerError() {
        wireMock.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(provider(baseUrl + "/latest").fetchLatestRates()).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_responseHasNoRates")
    void should_returnEmpty_when_responseHasNoRates() {
        wireMock.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"base\":\"USD\"}")));

        assertThat(provider(baseUrl + "/latest").fetchLatestRates()).isEmpty();
    }

    @Test
    @DisplayName("should_skipEntriesWithNullRate_when_jsonContainsNullValue")
    void should_skipEntriesWithNullRate_when_jsonContainsNullValue() {
        wireMock.stubFor(get(urlPathEqualTo("/latest"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"base":"USD","rates":{"EUR":0.92,"GBP":null}}
                                """)));

        Map<String, BigDecimal> rates = provider(baseUrl + "/latest").fetchLatestRates();

        assertThat(rates).containsOnlyKeys("EUR");
    }

    @Test
    @DisplayName("should_returnEmpty_when_apiUrlNotConfigured")
    void should_returnEmpty_when_apiUrlNotConfigured() {
        assertThat(provider("").fetchLatestRates()).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_apiUrlIsNull")
    void should_returnEmpty_when_apiUrlIsNull() {
        assertThat(provider(null).fetchLatestRates()).isEmpty();
    }

    private static HttpCurrencyRateProvider provider(String url) {
        return new HttpCurrencyRateProvider(RestClient.builder(), url);
    }
}
