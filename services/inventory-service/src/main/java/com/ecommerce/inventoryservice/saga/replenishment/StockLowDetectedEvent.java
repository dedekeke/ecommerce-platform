package com.ecommerce.inventoryservice.saga.replenishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Event payload for {@code stock.low.detected}.
 *
 * <p>Choreography contract: this is the only thing peers know about. Any field
 * removed here is a breaking change because there is no orchestrator to
 * version the schema centrally — each consumer parses this independently.
 *
 * <p>{@code eventId} is the idempotency key — every consumer dedupes on it.
 */
public record StockLowDetectedEvent(
        UUID eventId,
        Long productId,
        String sku,
        int currentQty,
        int threshold,
        Instant detectedAt
) {

    @JsonCreator
    public StockLowDetectedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("productId") Long productId,
            @JsonProperty("sku") String sku,
            @JsonProperty("currentQty") int currentQty,
            @JsonProperty("threshold") int threshold,
            @JsonProperty("detectedAt") Instant detectedAt
    ) {
        this.eventId = eventId == null ? UUID.randomUUID() : eventId;
        this.productId = productId;
        this.sku = sku;
        this.currentQty = currentQty;
        this.threshold = threshold;
        this.detectedAt = detectedAt == null ? Instant.now() : detectedAt;
    }
}
