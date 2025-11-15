package com.ecommerce.cartservice.exception;

/**
 * Exception thrown when a product is not available or out of stock
 */
public class ProductNotAvailableException extends RuntimeException {
    public ProductNotAvailableException(String message) {
        super(message);
    }
}
