package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/**
 * Event published when inventory is reserved for an order.
 * Indicates that stock has been temporarily allocated pending payment confirmation.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InventoryReservedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Reservation identifier
     */
    private String reservationId;

    /**
     * Order identifier
     */
    private String orderId;

    /**
     * Customer identifier
     */
    private String customerId;

    /**
     * Reserved items
     */
    private List<ReservedItem> items;

    /**
     * When the reservation expires
     */
    private Instant expiresAt;

    /**
     * Reservation status (RESERVED, COMMITTED, RELEASED, EXPIRED)
     */
    private String status;

    @Override
    public String getPartitionKey() {
        return orderId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedItem {
        private String productId;
        private String sku;
        private Integer quantity;
        private Integer availableQuantity;  // Quantity available after reservation
    }
}
