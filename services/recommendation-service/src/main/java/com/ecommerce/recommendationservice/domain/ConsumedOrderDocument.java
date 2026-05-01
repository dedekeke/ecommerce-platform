package com.ecommerce.recommendationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Idempotency ledger: each successfully ingested orderId is persisted here
 * so duplicate Kafka deliveries (at-least-once) or replays do not double-count
 * the co-occurrence matrix.
 *
 * <p>This mirrors the same pattern used in
 * {@code com.ecommerce.notificationservice.saga.replenishment.ConsumedReplenishmentEvent}.
 */
@Document(collection = "consumed_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedOrderDocument {

    @Id
    private String orderId;

    private Instant consumedAt;
}
