package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.ProductPriceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Looks up the current unit price of a product from product-service.
 *
 * <p>Uses a load-balanced {@link WebClient} addressing the service via
 * {@code lb://product-service}, so the instance is resolved through Eureka.
 * Failures (service down, timeout, unknown product) degrade to
 * {@link Optional#empty()} — the caller supplies a sensible fallback rather
 * than failing the whole batch.</p>
 */
@Slf4j
@Component
public class ProductPriceClient {

    private final WebClient webClient;
    private final Duration timeout;

    public ProductPriceClient(
        WebClient.Builder loadBalancedWebClientBuilder,
        @Value("${product.service.url:lb://product-service}") String productServiceBaseUrl,
        @Value("${product.service.price-lookup-timeout-ms:2000}") long timeoutMs
    ) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl(productServiceBaseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Fetch the current price for {@code productId}.
     *
     * @return the price if found and positive, otherwise empty
     */
    public Optional<BigDecimal> getCurrentPrice(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }
        try {
            ProductPriceResponse response = webClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .bodyToMono(ProductPriceResponse.class)
                .block(timeout);

            BigDecimal price = response == null ? null : response.getPrice();
            if (price == null || price.signum() <= 0) {
                log.warn("Product {} returned no usable price ({})", productId, price);
                return Optional.empty();
            }
            return Optional.of(price);
        } catch (Exception e) {
            log.warn("Price lookup failed for product {}: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }
}
