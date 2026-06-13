package com.ecommerce.paymentservice.service;

/** Thrown when an operation is illegal for the payment's current status. Maps to HTTP 409. */
public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
