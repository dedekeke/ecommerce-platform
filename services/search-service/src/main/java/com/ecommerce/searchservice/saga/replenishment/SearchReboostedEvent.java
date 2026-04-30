package com.ecommerce.searchservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/** Outcome event published on topic {@code search.reboosted.due-to-stock}. */
public record SearchReboostedEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        Instant reboostedAt
) {
}
