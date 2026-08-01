package com.ecommerce.cartservice.exception;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(null);

    private HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    @DisplayName("maps ProductServiceUnavailableException to a retryable 503 with a Retry-After header")
    void should_return503Retryable_when_productServiceUnavailable() {
        ProductServiceUnavailableException ex =
                new ProductServiceUnavailableException("Product service is temporarily unavailable. Please retry shortly.");

        ResponseEntity<ErrorResponse> response =
                handler.handleProductServiceUnavailableException(ex, requestFor("/api/cart/items"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(503);
        assertThat(response.getBody().getRetryable()).isTrue();
        assertThat(response.getBody().getPath()).isEqualTo("/api/cart/items");
    }

    @Test
    @DisplayName("falls back to the default Retry-After when no circuit breaker registry is available")
    void should_useDefaultRetryAfter_when_noRegistry() {
        ResponseEntity<ErrorResponse> response = handler.handleProductServiceUnavailableException(
                new ProductServiceUnavailableException("down"), requestFor("/api/cart/items"));

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("20");
    }

    @Test
    @DisplayName("derives Retry-After from the breaker's configured waitDurationInOpenState")
    void should_deriveRetryAfter_from_circuitBreakerConfig() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker("product-service"); // materialise with the 30s config
        GlobalExceptionHandler handlerWithRegistry = new GlobalExceptionHandler(registry);

        ResponseEntity<ErrorResponse> response = handlerWithRegistry.handleProductServiceUnavailableException(
                new ProductServiceUnavailableException("down"), requestFor("/api/cart/items"));

        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("30");
    }

    @Test
    @DisplayName("keeps ProductNotAvailableException mapped to 400 (distinct from unavailability)")
    void should_return400_when_productNotAvailable() {
        ProductNotAvailableException ex = new ProductNotAvailableException("Product not found: p1");

        ResponseEntity<ErrorResponse> response =
                handler.handleProductNotAvailableException(ex, requestFor("/api/cart/items"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRetryable()).isNull();
    }
}
