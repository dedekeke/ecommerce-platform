package com.ecommerce.notificationservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/** Outcome event published on topic {@code admin.notified.due-to-stock}. */
public record AdminNotifiedEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        String adminEmail,
        Instant notifiedAt
) {
}
