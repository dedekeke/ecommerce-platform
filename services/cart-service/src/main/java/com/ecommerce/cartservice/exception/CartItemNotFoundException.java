package com.ecommerce.cartservice.exception;

/**
 * Exception thrown when a cart item is not found
 */
public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String message) {
        super(message);
    }
}
