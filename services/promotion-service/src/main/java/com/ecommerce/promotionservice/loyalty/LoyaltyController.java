package com.ecommerce.promotionservice.loyalty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loyalty endpoints (§3.7). The checkout flow calls this to apply tier
 * discounts when computing the cart total.
 */
@RestController
@RequestMapping("/api/promotions/loyalty")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Loyalty", description = "Tiered loyalty program endpoints")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get loyalty status for a user",
            description = "Returns the current tier, discount, lifetime spend, and the spend remaining to reach the next tier.")
    public ResponseEntity<LoyaltyResponse> getLoyalty(@PathVariable String userId) {
        log.debug("GET /api/promotions/loyalty/{}", userId);
        return ResponseEntity.ok(loyaltyService.getLoyaltyForUser(userId));
    }
}
