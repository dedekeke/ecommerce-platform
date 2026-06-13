package com.ecommerce.paymentservice.service;

/** Thrown when a payment cannot be located by intent id or order id. Maps to HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
