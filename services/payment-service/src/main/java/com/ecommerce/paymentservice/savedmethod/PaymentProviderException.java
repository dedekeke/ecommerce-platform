package com.ecommerce.paymentservice.savedmethod;

/** Raised when the upstream tokenization provider (Stripe) fails to attach a payment method. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
