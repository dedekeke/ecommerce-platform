package com.ecommerce.inventoryservice.saga.replenishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Event payload for {@code stock.replenished} — the compensation trigger.
 *
 * <p>Each peer service listens for this and reverses what it did when
 * {@link StockLowDetectedEvent} fired (resume promotion, clear the search
 * penalty, send "back in stock" email).
 *
 * <p>Note that "compensation" in choreography is itself an event — there is
 * no central state machine deciding when to compensate; compensation just
 * means another fan-out.
 */
public record StockReplenishedEvent(
        UUID eventId,
        Long productId,
        String sku,
        int currentQty,
        Instant replenishedAt
) {

    @JsonCreator
    public StockReplenishedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("productId") Long productId,
            @JsonProperty("sku") String sku,
            @JsonProperty("currentQty") int currentQty,
            @JsonProperty("replenishedAt") Instant replenishedAt
    ) {
        this.eventId = eventId == null ? UUID.randomUUID() : eventId;
        this.productId = productId;
        this.sku = sku;
        this.currentQty = currentQty;
        this.replenishedAt = replenishedAt == null ? Instant.now() : replenishedAt;
    }
}
