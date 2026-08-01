package com.ecommerce.promotionservice.saga.replenishment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Local copy of the {@code stock.low.detected} contract.
 *
 * <p>Choreography pedagogy point: this class is duplicated in every consuming
 * service on purpose. There is no shared library because in real distributed
 * systems the publisher and consumer are owned by different teams and shipped
 * on different schedules. Sharing a class would couple their release cycles
 * and silently break the loose-coupling promise of the pattern.
 *
 * <p>The contract is the topic + JSON shape, not the Java class.
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
