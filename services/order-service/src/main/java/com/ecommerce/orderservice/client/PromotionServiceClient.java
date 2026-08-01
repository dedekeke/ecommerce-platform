package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.LoyaltyResult;
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
 *
 * <p>Calls carry a SERVICE credential (the shared {@value #INTERNAL_TOKEN_HEADER}
 * secret), never the end user's JWT. {@code POST /api/promotions/apply} is
 * restricted to service callers on the promotion-service side, and the checkout
 * saga applies a promotion on the GUEST path too — where no user JWT exists —
 * so a caller-owned credential is the only one available on both paths.
 */
@Slf4j
@Component
public class PromotionServiceClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public PromotionServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${promotion.service.url:http://promotion-service:8090}") String promotionServiceUrl,
        @Value("${promotion.service.internal-token:}") String internalServiceToken
    ) {
        RestClient.Builder builder = restClientBuilder.baseUrl(promotionServiceUrl);
        if (internalServiceToken != null && !internalServiceToken.isBlank()) {
            builder.defaultHeader(INTERNAL_TOKEN_HEADER, internalServiceToken);
        } else {
            // Local/dev promotion-service runs with security.enabled=false and
            // ignores the header; any secured deployment rejects /apply without it.
            log.warn("No promotion service internal token configured — POST /api/promotions/apply "
                + "will be rejected by a security-enabled promotion-service");
        }
        this.restClient = builder.build();
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
     * <p>The endpoint re-validates before redeeming, so it needs the same
     * {@link PromotionValidationRequest} body as {@code /validate} — posting a
     * bare code string is rejected (unsupported media type) and the usage
     * counter is never incremented.
     *
     * @param promotionCode  Promotion code to apply
     * @param purchaseAmount Pre-discount purchase amount the code was validated against
     * @return Discount result
     */
    @CircuitBreaker(name = "promotion-service", fallbackMethod = "applyPromotionFallback")
    @Retry(name = "promotion-service")
    @Bulkhead(name = "promotion-service")
    public DiscountResult applyPromotion(String promotionCode, BigDecimal purchaseAmount) {
        log.info("Applying promotion code: {}", promotionCode);

        PromotionValidationRequest request = PromotionValidationRequest.builder()
            .code(promotionCode)
            .purchaseAmount(purchaseAmount)
            .build();

        DiscountResult result = restClient.post()
            .uri("/api/promotions/apply")
            .body(request)
            .retrieve()
            .body(DiscountResult.class);

        log.info("Promotion applied successfully: {}", promotionCode);

        return result;
    }

    /**
     * Fetch the loyalty tier discount percentage (0..100) for a user. Used by
     * the checkout pricing path to apply a tier discount on top of any
     * promotion code. Degrades gracefully to {@link BigDecimal#ZERO} when the
     * user is unknown or promotion-service is unavailable, so checkout never
     * fails on a loyalty lookup.
     *
     * @param userId the customer id (Auth0 sub)
     * @return the tier discount percent, never null; {@code ZERO} on any miss
     */
    @CircuitBreaker(name = "promotion-service", fallbackMethod = "loyaltyDiscountFallback")
    @Retry(name = "promotion-service")
    @Bulkhead(name = "promotion-service")
    public BigDecimal getLoyaltyDiscountPercent(String userId) {
        if (userId == null || userId.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            LoyaltyResult result = restClient.get()
                .uri("/api/promotions/loyalty/{userId}", userId)
                .retrieve()
                .body(LoyaltyResult.class);

            BigDecimal percent = result == null ? null : result.getDiscountPercent();
            log.debug("Loyalty discount for user {}: {}%", userId, percent);
            return percent == null ? BigDecimal.ZERO : percent;
        } catch (RestClientException e) {
            log.warn("Promotion service unavailable during loyalty lookup for {}: {}",
                userId, e.getMessage());
            return loyaltyDiscountFallback(userId, e);
        }
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
    /**
     * Fallback for getLoyaltyDiscountPercent — no discount when the breaker is
     * open or the call fails. Checkout proceeds at the undiscounted price.
     */
    private BigDecimal loyaltyDiscountFallback(String userId, Throwable t) {
        // Loyalty is a non-critical enhancement: a miss simply means no tier
        // discount and checkout proceeds. Log at WARN, not ERROR, so this
        // expected degradation does not pollute error dashboards / alerting.
        log.warn("Promotion Service unavailable for loyalty lookup. User: {}, Error: {}",
            userId, t.getMessage());
        return BigDecimal.ZERO;
    }

    private DiscountResult applyPromotionFallback(String promotionCode, BigDecimal purchaseAmount, Throwable t) {
        log.error("Promotion Service unavailable for applying promotion. Code: {}, Error: {}",
            promotionCode, t.getMessage());

        return DiscountResult.builder()
            .valid(false)
            .message("Failed to apply promotion due to service unavailability.")
            .discountAmount(BigDecimal.ZERO)
            .build();
    }
}
