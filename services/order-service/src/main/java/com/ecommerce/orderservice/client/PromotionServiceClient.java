package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * REST client for communicating with Promotion Service
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
     * Validate a promotion code for a given purchase amount
     *
     * @param request Promotion validation request
     * @return Discount result with validation status and discount details
     */
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
            log.error("Error calling Promotion Service: {}", e.getMessage(), e);

            // Return invalid result with original amount
            return DiscountResult.builder()
                .valid(false)
                .message("Promotion service is currently unavailable. Please try again later.")
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(request.getPurchaseAmount())
                .build();
        }
    }

    /**
     * Apply a promotion code (increments usage count)
     *
     * @param promotionCode Promotion code to apply
     * @return Discount result
     */
    public DiscountResult applyPromotion(String promotionCode) {
        log.info("Applying promotion code: {}", promotionCode);

        try {
            DiscountResult result = restClient.post()
                .uri("/api/promotions/apply")
                .body(promotionCode)
                .retrieve()
                .body(DiscountResult.class);

            log.info("Promotion applied successfully: {}", promotionCode);

            return result;

        } catch (RestClientException e) {
            log.error("Error calling Promotion Service: {}", e.getMessage(), e);

            return DiscountResult.builder()
                .valid(false)
                .message("Failed to apply promotion: " + e.getMessage())
                .discountAmount(BigDecimal.ZERO)
                .build();
        }
    }
}
