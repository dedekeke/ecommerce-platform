package com.ecommerce.userservice.exception;

/**
 * Thrown when a wishlist entry is not found for the given (userId, productId) pair.
 */
public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(String message) {
        super(message);
    }
}
