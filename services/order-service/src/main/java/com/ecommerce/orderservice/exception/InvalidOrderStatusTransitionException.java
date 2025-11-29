package com.ecommerce.orderservice.exception;

import com.ecommerce.orderservice.domain.enums.OrderStatus;

/**
 * Exception thrown when an invalid order status transition is attempted
 */
public class InvalidOrderStatusTransitionException extends RuntimeException {
    public InvalidOrderStatusTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format(
            "Invalid status transition from %s to %s",
            currentStatus,
            targetStatus
        ));
    }

    public InvalidOrderStatusTransitionException(String message) {
        super(message);
    }
}
