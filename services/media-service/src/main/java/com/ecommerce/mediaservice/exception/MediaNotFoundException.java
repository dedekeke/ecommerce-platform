package com.ecommerce.mediaservice.exception;

/**
 * Exception thrown when media is not found
 */
public class MediaNotFoundException extends RuntimeException {

    public MediaNotFoundException(String id) {
        super("Media not found with id: " + id);
    }

    public MediaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
