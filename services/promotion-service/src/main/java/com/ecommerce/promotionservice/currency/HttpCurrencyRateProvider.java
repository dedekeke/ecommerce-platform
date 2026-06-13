package com.ecommerce.promotionservice.currency;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link CurrencyRateProvider} backed by an HTTP FX-rate API.
 *
 * <p>The endpoint is supplied via the {@code CURRENCY_RATE_API_URL} environment
 * variable (no default — the refresh job is feature-flagged off by default, so
 * a missing URL never breaks startup). The expected response shape matches the
 * widely-used open FX APIs:
 * <pre>{@code { "base": "USD", "rates": { "EUR": 0.92, "GBP": 0.79, ... } } }</pre>
 *
 * <p>Network/parse failures are surfaced as an empty map rather than an
 * exception so the scheduled job degrades to a no-op (keeping the last known
 * seeded rates) instead of failing loudly on a transient provider outage.
 */
@Slf4j
@Component
public class HttpCurrencyRateProvider implements CurrencyRateProvider {

    private final RestClient restClient;
    private final String apiUrl;

    public HttpCurrencyRateProvider(
            RestClient.Builder restClientBuilder,
            @Value("${currency.rate.api-url:${CURRENCY_RATE_API_URL:}}") String apiUrl,
            @Value("${currency.rate.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${currency.rate.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.apiUrl = apiUrl;
    }

    @Override
    public Map<String, BigDecimal> fetchLatestRates() {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("CURRENCY_RATE_API_URL is not configured; skipping rate fetch");
            return Map.of();
        }

        try {
            FxResponse response = restClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .body(FxResponse.class);

            if (response == null || response.rates == null || response.rates.isEmpty()) {
                log.warn("Currency rate provider returned no rates from {}", apiUrl);
                return Map.of();
            }

            Map<String, BigDecimal> normalized = new LinkedHashMap<>();
            response.rates.forEach((code, rate) -> {
                if (code != null && rate != null && rate.signum() > 0) {
                    normalized.put(code.trim().toUpperCase(), rate);
                }
            });
            log.info("Fetched {} currency rate(s) from provider", normalized.size());
            return normalized;
        } catch (RestClientException e) {
            log.error("Failed to fetch currency rates from {}: {}", apiUrl, e.getMessage());
            return Map.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class FxResponse {
        public String base;
        public Map<String, BigDecimal> rates;
    }
}
