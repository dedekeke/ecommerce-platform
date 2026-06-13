package com.ecommerce.paymentservice.gateway;

import java.math.BigDecimal;

/**
 * Abstraction over the PaymentIntent lifecycle of an external gateway.
 *
 * <p>Selected at runtime via {@code payment.provider}: {@code mock} (default) wires
 * {@link MockPaymentIntentProvider}; {@code stripe} wires {@link StripePaymentIntentProvider}.
 * The mock keeps tests and local development free of any real Stripe credentials.</p>
 */
public interface PaymentIntentProvider {

    PaymentGatewayResponse createPaymentIntent(String orderId, String userId, BigDecimal amount, String currency);

    PaymentGatewayResponse confirmPayment(String paymentIntentId, String paymentMethodId);

    PaymentGatewayResponse refundPayment(String paymentIntentId, BigDecimal amount, String reason);
}
