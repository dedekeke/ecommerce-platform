package com.ecommerce.orderservice.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order status enumeration with state transition rules
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    /**
     * Get allowed transitions from current status
     */
    public Set<OrderStatus> getAllowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            // A paid (CONFIRMED) order can ship directly, or go through the
            // optional PROCESSING (picking/packing) stage first — both reach SHIPPED.
            case CONFIRMED -> EnumSet.of(PROCESSING, SHIPPED, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED -> EnumSet.of(REFUNDED);
            case CANCELLED, REFUNDED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    /**
     * Check if transition to target status is allowed
     */
    public boolean canTransitionTo(OrderStatus targetStatus) {
        return getAllowedTransitions().contains(targetStatus);
    }

    /**
     * Check if order is in a terminal state
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }

    /**
     * Check if order can be cancelled
     */
    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING;
    }

    /**
     * Check if order can be refunded
     */
    public boolean isRefundable() {
        return this == DELIVERED;
    }
}
