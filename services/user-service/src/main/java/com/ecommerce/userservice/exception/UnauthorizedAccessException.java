package com.ecommerce.userservice.exception;

/**
 * Unauthorized Access Exception
 *
 * Thrown when a user tries to access resources they don't own.
 */
public class UnauthorizedAccessException extends RuntimeException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public static UnauthorizedAccessException forAddress(Long addressId) {
        return new UnauthorizedAccessException(
                "You are not authorized to access address with ID: " + addressId
        );
    }

}
