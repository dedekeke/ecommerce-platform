package com.ecommerce.cartservice.client;

import com.ecommerce.cartservice.dto.ProductDto;
import com.ecommerce.cartservice.exception.ProductServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience boundary in front of {@link ProductServiceClient}.
 *
 * <p>Design (mirrors order-service's annotation-based Resilience4j convention):
 * annotations must sit on a Spring-managed bean's method for the AOP aspects to
 * apply — they cannot decorate the Feign interface itself (Feign builds its own
 * proxy), so this thin wrapper is the seam.
 *
 * <ul>
 *   <li><b>CircuitBreaker</b> ("product-service"): once product-service browns
 *       out, the breaker opens and subsequent calls fail fast in microseconds
 *       instead of every add-to-cart burning the full read timeout.</li>
 *   <li><b>Bulkhead</b> ("product-service"): caps concurrent in-flight calls so
 *       a brownout cannot exhaust the whole request thread pool and take down
 *       the rest of the cart API.</li>
 *   <li><b>TimeLimiter is intentionally omitted.</b> Resilience4j's TimeLimiter
 *       requires a {@code CompletableFuture}-returning method plus a dedicated
 *       thread pool; forcing this blocking Feign call async adds complexity for
 *       no gain. Per-call latency is instead bounded by the product-service
 *       Feign {@code readTimeout} (application.yml), and slow calls are counted
 *       toward opening the breaker via {@code slowCallDurationThreshold}.</li>
 *   <li><b>Retry is intentionally omitted.</b> This sits on the checkout funnel;
 *       retrying a brown-out backend only adds latency and load. We fail fast.</li>
 * </ul>
 *
 * <p><b>Fallback semantics — FAIL FAST, never fake data:</b> when the breaker is
 * open, the bulkhead is full, or the call fails with a transport/5xx error, we
 * throw {@link ProductServiceUnavailableException} (surfaced as HTTP 503 with a
 * retryable hint). We never serve stale prices or fabricated product data. A
 * genuine 4xx (e.g. 404 product-not-found) is a real product-service response,
 * not an outage, so it is re-thrown unchanged for the caller to interpret.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceGateway {

    static final String INSTANCE = "product-service";

    private final ProductServiceClient productServiceClient;

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "getProductByIdFallback")
    @Bulkhead(name = INSTANCE)
    public ProductDto getProductById(String productId) {
        return productServiceClient.getProductById(productId);
    }

    /**
     * A 4xx from product-service is a legitimate answer about the product (e.g.
     * 404 not found), not a brownout — re-throw it so the service layer maps it
     * to a product-not-available (400) exactly as before. Not recorded as a
     * circuit-breaker failure (see recordExceptions in application.yml).
     */
    @SuppressWarnings("unused")
    private ProductDto getProductByIdFallback(String productId, FeignException.FeignClientException ex) {
        throw ex;
    }

    /**
     * Catch-all: breaker open (CallNotPermittedException), bulkhead full
     * (BulkheadFullException), 5xx, connection refused, read timeout — the
     * backend is not reachably healthy, so fail fast with a typed, retryable
     * error rather than blocking or fabricating data.
     */
    @SuppressWarnings("unused")
    private ProductDto getProductByIdFallback(String productId, Throwable t) {
        log.warn("product-service unavailable for productId={} — failing fast: {}",
                productId, t.toString());
        throw new ProductServiceUnavailableException(
                "Product service is temporarily unavailable. Please retry shortly.", t);
    }
}
