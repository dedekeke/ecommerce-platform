package com.ecommerce.promotionservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome event for the compensation path. Topic:
 * {@code promotion.resumed.due-to-stock}.
 */
public record PromotionResumedEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        int resumedPromotionCount,
        Instant resumedAt
) {
}
