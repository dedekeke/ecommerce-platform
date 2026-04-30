package com.ecommerce.searchservice.saga.replenishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Local copy of the {@code stock.low.detected} contract — the contract is
 * the JSON shape on the wire, not this Java class. Each consuming service
 * owns its own copy so they can release independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        this.eventId = eventId;
        this.productId = productId;
        this.sku = sku;
        this.currentQty = currentQty;
        this.threshold = threshold;
        this.detectedAt = detectedAt;
    }
}
