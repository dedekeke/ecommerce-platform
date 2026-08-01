package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.RecommendationResponse;
import com.ecommerce.recommendationservice.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read-only API for product and per-user recommendations.
 *
 * <p>Per-product is anonymous so it can power "people who bought also bought"
 * widgets on PDP/PLP without forcing login. Per-user is gated on authentication
 * at the gateway level (route requires JWT) and at the service level (security
 * filter chain).
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Validated
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<RecommendationResponse>> getProductRecommendations(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        log.info("GET /api/recommendations/product/{} limit={}", productId, limit);
        return ResponseEntity.ok(recommendationService.getProductRecommendations(productId, limit));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RecommendationResponse>> getUserRecommendations(
            @PathVariable @NotBlank String userId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        log.info("GET /api/recommendations/user/{} limit={}", userId, limit);
        return ResponseEntity.ok(recommendationService.getUserRecommendations(userId, limit));
    }
}
