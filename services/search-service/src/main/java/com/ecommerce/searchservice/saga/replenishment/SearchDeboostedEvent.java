package com.ecommerce.searchservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/** Outcome event published on topic {@code search.deboosted.due-to-stock}. */
public record SearchDeboostedEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        Instant deboostedAt
) {
}
