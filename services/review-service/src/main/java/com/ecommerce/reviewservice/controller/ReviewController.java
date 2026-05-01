package com.ecommerce.reviewservice.controller;

import com.ecommerce.reviewservice.dto.CreateReviewRequest;
import com.ecommerce.reviewservice.dto.ReviewResponse;
import com.ecommerce.reviewservice.dto.ReviewSummaryResponse;
import com.ecommerce.reviewservice.service.ReviewService;
import com.ecommerce.reviewservice.service.ReviewSortMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public-facing review API.
 *
 * <p>Read endpoints (list + summary) are anonymous so PDP widgets can render
 * without forcing login. Write endpoints (create, helpful, delete) require a
 * valid Auth0 JWT — enforced both at the security filter chain and via
 * {@code @AuthenticationPrincipal Jwt} on the handler.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponse> create(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = subject(jwt);
        log.info("POST /api/reviews productId={} by userId={}", request.productId(), userId);
        ReviewResponse response = reviewService.createReview(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Map<String, Object>> listByProduct(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "helpful") String sort) {

        ReviewSortMode mode = ReviewSortMode.from(sort);
        Page<ReviewResponse> result = reviewService.listProductReviews(productId, page, size, mode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("sort", mode.name().toLowerCase());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ReviewSummaryResponse> summary(@PathVariable @NotBlank String productId) {
        return ResponseEntity.ok(reviewService.getProductSummary(productId));
    }

    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<ReviewResponse> markHelpful(
            @PathVariable @NotBlank String reviewId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = subject(jwt);
        log.info("POST /api/reviews/{}/helpful by userId={}", reviewId, userId);
        return ResponseEntity.ok(reviewService.markHelpful(reviewId, userId));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(
            @PathVariable @NotBlank String reviewId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = subject(jwt);
        boolean isAdmin = hasAdminRole(jwt);
        log.info("DELETE /api/reviews/{} by userId={} admin={}", reviewId, userId, isAdmin);
        reviewService.deleteReview(reviewId, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    private static String subject(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    private static boolean hasAdminRole(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        Object roles = jwt.getClaims().get("https://ecommerce.com/roles");
        if (roles instanceof Iterable<?> iter) {
            for (Object r : iter) {
                if (r != null && r.toString().equalsIgnoreCase("admin")) {
                    return true;
                }
            }
        }
        return jwt.getClaimAsStringList("scope") != null
                && jwt.getClaimAsStringList("scope").stream().anyMatch(s -> s.equalsIgnoreCase("admin"));
    }

    /**
     * Allows tests / future authorities to flag admins via Spring's authority
     * chain (used by SecurityContextTestHelper). Not currently invoked but
     * referenced here to document the intent — see
     * {@code @PreAuthorize} alternative if we move role checks off claims.
     */
    @SuppressWarnings("unused")
    private static boolean hasAdminAuthority(Iterable<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return false;
        }
        for (GrantedAuthority a : authorities) {
            if ("ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "SCOPE_admin".equalsIgnoreCase(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
