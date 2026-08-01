package com.ecommerce.promotionservice.saga.replenishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        this.eventId = eventId;
        this.productId = productId;
        this.sku = sku;
        this.currentQty = currentQty;
        this.replenishedAt = replenishedAt;
    }
}
