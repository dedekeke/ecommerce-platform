package com.ecommerce.orderservice.exception;

/** Thrown when a client-supplied userId differs from the authenticated JWT subject. */
public class UserMismatchException extends RuntimeException {

    public UserMismatchException(String message) {
        super(message);
    }
}
