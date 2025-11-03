package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when an order status changes.
 * Used to notify interested services about order lifecycle changes.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderStatusUpdatedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Order identifier
     */
    private String orderId;

    /**
     * Order number
     */
    private String orderNumber;

    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Previous status
     */
    private String previousStatus;

    /**
     * New status (PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED)
     */
    private String newStatus;

    /**
     * Reason for status change (e.g., "Payment confirmed", "Shipped via UPS")
     */
    private String reason;

    /**
     * Additional metadata about the status change
     */
    private String metadata;

    @Override
    public String getPartitionKey() {
        return orderId;
    }
}
