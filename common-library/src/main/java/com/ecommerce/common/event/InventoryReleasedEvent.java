package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Event published when inventory reservation is released.
 * This happens when an order is cancelled, payment fails, or reservation expires.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryReleasedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Reservation identifier
     */
    private String reservationId;

    /**
     * Order identifier (if applicable)
     */
    private String orderId;

    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Released items
     */
    private List<ReleasedItem> items;

    /**
     * Reason for release (PAYMENT_FAILED, ORDER_CANCELLED, EXPIRED, etc.)
     */
    private String reason;

    @Override
    public String getPartitionKey() {
        return orderId != null ? orderId : reservationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleasedItem {
        private String productId;
        private String sku;
        private Integer quantity;
        private Integer newAvailableQuantity;  // Quantity available after release
    }
}
