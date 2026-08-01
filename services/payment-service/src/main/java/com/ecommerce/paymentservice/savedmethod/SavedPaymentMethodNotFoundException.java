package com.ecommerce.paymentservice.savedmethod;

/**
 * Thrown when a saved payment method id cannot be resolved for the acting user.
 * Mapped to HTTP 404 by {@code PaymentApiExceptionHandler}.
 */
public class SavedPaymentMethodNotFoundException extends RuntimeException {

    public SavedPaymentMethodNotFoundException(String message) {
        super(message);
    }
}
