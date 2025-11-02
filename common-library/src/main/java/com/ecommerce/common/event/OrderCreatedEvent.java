package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Event published when a new order is created.
 * This event triggers downstream actions like inventory reservation, payment processing, and notifications.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Unique order identifier
     */
    private String orderId;

    /**
     * Human-readable order number (e.g., ORD-2025-00001)
     */
    private String orderNumber;

    /**
     * User who placed the order
     */
    private String customerId;

    /**
     * Order items with product details
     */
    private List<OrderItem> items;

    /**
     * Order subtotal (sum of item prices)
     */
    private BigDecimal subtotal;

    /**
     * Tax amount
     */
    private BigDecimal tax;

    /**
     * Shipping cost
     */
    private BigDecimal shippingCost;

    /**
     * Total order amount
     */
    private BigDecimal total;

    /**
     * Order status (typically PENDING for new orders)
     */
    private String status;

    /**
     * Shipping address
     */
    private Address shippingAddress;

    /**
     * Payment method
     */
    private String paymentMethod;

    /**
     * Promotion code applied (if any)
     */
    private String promotionCode;

    /**
     * Discount amount (if promotion applied)
     */
    private BigDecimal discountAmount;

    /**
     * Override partition key to use orderId for consistent ordering
     */
    @Override
    public String getPartitionKey() {
        return orderId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private String productName;
        private String sku;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }
}
