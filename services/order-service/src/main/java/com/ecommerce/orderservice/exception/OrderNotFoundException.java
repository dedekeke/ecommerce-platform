package com.ecommerce.orderservice.exception;

/**
 * Exception thrown when an order is not found
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(String orderId, String userId) {
        super(String.format("Order not found with id: %s for user: %s", orderId, userId));
    }
}
