package com.ecommerce.paymentservice.api;

/** Thrown when a request body claims a userId that differs from the authenticated token subject. */
public class UserMismatchException extends RuntimeException {

    public UserMismatchException(String message) {
        super(message);
    }
}
