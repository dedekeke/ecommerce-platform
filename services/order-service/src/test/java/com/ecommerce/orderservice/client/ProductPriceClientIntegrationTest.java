package com.ecommerce.orderservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ProductPriceClient} against a WireMock stand-in
 * for product-service. Exercises the real WebClient HTTP path (JSON parse,
 * timeout, graceful fallback) without contacting a live service. The
 * {@code lb://} scheme is purely instance-resolution and is substituted with
 * the WireMock {@code http://localhost} base URL here.
 */
@DisplayName("ProductPriceClient — WireMock integration")
class ProductPriceClientIntegrationTest {

    private WireMockServer wireMock;
    private ProductPriceClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        client = new ProductPriceClient(
            WebClient.builder(), "http://localhost:" + wireMock.port(), 2000);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("should_returnCurrentPrice_when_productServiceReturnsBody")
    void should_returnCurrentPrice_when_productServiceReturnsBody() {
        wireMock.stubFor(get(urlEqualTo("/api/products/prod-1"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"prod-1\",\"price\":29.99}")));

        Optional<BigDecimal> price = client.getCurrentPrice("prod-1");

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo("29.99");
    }

    @Test
    @DisplayName("should_returnEmpty_when_productNotFound")
    void should_returnEmpty_when_productNotFound() {
        wireMock.stubFor(get(urlEqualTo("/api/products/missing"))
            .willReturn(aResponse().withStatus(404)));

        assertThat(client.getCurrentPrice("missing")).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_priceIsZeroOrNegative")
    void should_returnEmpty_when_priceIsZeroOrNegative() {
        wireMock.stubFor(get(urlEqualTo("/api/products/prod-2"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"prod-2\",\"price\":0}")));

        assertThat(client.getCurrentPrice("prod-2")).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_lookupTimesOut")
    void should_returnEmpty_when_lookupTimesOut() {
        ProductPriceClient shortTimeout = new ProductPriceClient(
            WebClient.builder(), "http://localhost:" + wireMock.port(), 200);
        wireMock.stubFor(get(urlEqualTo("/api/products/slow"))
            .willReturn(aResponse()
                .withFixedDelay(1000)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"slow\",\"price\":10.00}")));

        assertThat(shortTimeout.getCurrentPrice("slow")).isEmpty();
    }

    @Test
    @DisplayName("should_returnEmpty_when_productIdBlank")
    void should_returnEmpty_when_productIdBlank() {
        assertThat(client.getCurrentPrice("  ")).isEmpty();
    }
}
