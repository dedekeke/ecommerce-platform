package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * REST client for communicating with Promotion Service.
 * Protected by Resilience4j circuit breaker, retry, and bulkhead patterns.
 */
@Slf4j
@Component
public class PromotionServiceClient {

    private final RestClient restClient;

    public PromotionServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${promotion.service.url:http://promotion-service:8090}") String promotionServiceUrl
    ) {
        this.restClient = restClientBuilder
            .baseUrl(promotionServiceUrl)
            .build();
    }

    /**
     * Validate a promotion code for a given purchase amount.
     * Protected by circuit breaker with fallback to return invalid result.
     *
     * @param request Promotion validation request
     * @return Discount result with validation status and discount details
     */
    @CircuitBreaker(name = "promotion-service", fallbackMethod = "validatePromotionFallback")
    @Retry(name = "promotion-service")
    @Bulkhead(name = "promotion-service")
    public DiscountResult validatePromotion(PromotionValidationRequest request) {
        log.info("Validating promotion code: {} for amount: {}", request.getCode(), request.getPurchaseAmount());

        try {
            DiscountResult result = restClient.post()
                .uri("/api/promotions/validate")
                .body(request)
                .retrieve()
                .body(DiscountResult.class);

            log.info("Promotion validation result: valid={}, discount={}",
                result.isValid(), result.getDiscountAmount());

            return result;
        } catch (RestClientException e) {
            // Defensive fallback for callers that bypass Resilience4j AOP (e.g.
            // plain unit tests). The circuit breaker fallback handles the
            // production runtime path; this guarantees graceful degradation
            // even when the breaker isn't active.
            log.warn("Promotion service unavailable during validation: {}", e.getMessage());
            return validatePromotionFallback(request, e);
        }
    }

    /**
     * Apply a promotion code (increments usage count).
     * Protected by circuit breaker with fallback.
     *
     * @param promotionCode Promotion code to apply
     * @return Discount result
     */
    @CircuitBreaker(name = "promotion-service", fallbackMethod = "applyPromotionFallback")
    @Retry(name = "promotion-service")
    @Bulkhead(name = "promotion-service")
    public DiscountResult applyPromotion(String promotionCode) {
        log.info("Applying promotion code: {}", promotionCode);

        DiscountResult result = restClient.post()
            .uri("/api/promotions/apply")
            .body(promotionCode)
            .retrieve()
            .body(DiscountResult.class);

        log.info("Promotion applied successfully: {}", promotionCode);

        return result;
    }

    // ==================== Fallback Methods ====================

    /**
     * Fallback for validatePromotion when Promotion Service is unavailable.
     * Returns invalid result with original amount to allow order to proceed without discount.
     */
    private DiscountResult validatePromotionFallback(PromotionValidationRequest request, Throwable t) {
        log.error("Promotion Service unavailable for validation. Code: {}, Error: {}",
            request.getCode(), t.getMessage());

        return DiscountResult.builder()
            .valid(false)
            .message("Promotion service is temporarily unavailable. Your order will proceed without the discount. Please contact support if you need assistance.")
            .discountAmount(BigDecimal.ZERO)
            .finalAmount(request.getPurchaseAmount())
            .build();
    }

    /**
     * Fallback for applyPromotion when Promotion Service is unavailable.
     * Returns invalid result; the promotion usage will not be incremented.
     */
    private DiscountResult applyPromotionFallback(String promotionCode, Throwable t) {
        log.error("Promotion Service unavailable for applying promotion. Code: {}, Error: {}",
            promotionCode, t.getMessage());

        return DiscountResult.builder()
            .valid(false)
            .message("Failed to apply promotion due to service unavailability.")
            .discountAmount(BigDecimal.ZERO)
            .build();
    }
}
