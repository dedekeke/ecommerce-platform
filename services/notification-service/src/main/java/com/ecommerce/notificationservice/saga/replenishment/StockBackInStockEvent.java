package com.ecommerce.notificationservice.saga.replenishment;

import java.time.Instant;
import java.util.UUID;

/** Outcome event published on topic {@code admin.stock-back.due-to-stock}. */
public record StockBackInStockEvent(
        UUID eventId,
        UUID causedByEventId,
        Long productId,
        String adminEmail,
        Instant notifiedAt
) {
}
