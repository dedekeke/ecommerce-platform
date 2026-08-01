package com.ecommerce.notificationservice.saga.replenishment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Mongo dedup record for the replenishment choreography.
 *
 * <p>Notification-service is the only Mongo-backed participant in this saga,
 * so its idempotency ledger is a tiny Mongo collection rather than a JPA
 * table. The shape is the same: store every successfully processed
 * {@code eventId}, drop replays.
 */
@Document(collection = "replenishment_consumed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedReplenishmentEvent {

    @Id
    private UUID eventId;

    private String topic;

    private Instant consumedAt;
}
