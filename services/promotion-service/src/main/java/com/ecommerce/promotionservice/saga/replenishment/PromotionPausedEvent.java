package com.ecommerce.promotionservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/**
 * Outcome event published after promotion-service pauses promotions for a
 * low-stock product. Topic: {@code promotion.paused.due-to-stock}.
 *
 * <p>No peer currently consumes this — it exists as the audit trail and as
 * the obvious extension point: an analytics-service or admin-dashboard MFE
 * can subscribe without any code change here.
 */
public record PromotionPausedEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        int pausedPromotionCount,
        Instant pausedAt
) {
}
