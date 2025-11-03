package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events in the e-commerce platform.
 * All events must extend this class to ensure consistent metadata.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class, name = "ORDER_CREATED"),
    @JsonSubTypes.Type(value = OrderStatusUpdatedEvent.class, name = "ORDER_STATUS_UPDATED"),
    @JsonSubTypes.Type(value = PaymentCompletedEvent.class, name = "PAYMENT_COMPLETED"),
    @JsonSubTypes.Type(value = PaymentFailedEvent.class, name = "PAYMENT_FAILED"),
    @JsonSubTypes.Type(value = InventoryReservedEvent.class, name = "INVENTORY_RESERVED"),
    @JsonSubTypes.Type(value = InventoryReleasedEvent.class, name = "INVENTORY_RELEASED"),
    @JsonSubTypes.Type(value = ProductCreatedEvent.class, name = "PRODUCT_CREATED"),
    @JsonSubTypes.Type(value = ProductUpdatedEvent.class, name = "PRODUCT_UPDATED"),
    @JsonSubTypes.Type(value = UserCreatedEvent.class, name = "USER_CREATED")
})
public abstract class BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this event instance
     */
    private String eventId;

    /**
     * Type of the event (e.g., ORDER_CREATED, PAYMENT_COMPLETED)
     */
    private String eventType;

    /**
     * Timestamp when the event was created
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    /**
     * Version of the event schema (for schema evolution)
     */
    private String version;

    /**
     * Correlation ID for tracing across services
     */
    private String correlationId;

    /**
     * Service that published this event
     */
    private String source;

    /**
     * User ID who triggered the event (if applicable)
     */
    private String userId;

    /**
     * Initialize common event metadata
     */
    protected void initializeMetadata(String eventType, String source) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
        this.version = "1.0";
        this.source = source;
    }

    /**
     * Get the partition key for Kafka (default is eventId)
     * Override this method to customize partitioning strategy
     */
    public String getPartitionKey() {
        return eventId;
    }
}
