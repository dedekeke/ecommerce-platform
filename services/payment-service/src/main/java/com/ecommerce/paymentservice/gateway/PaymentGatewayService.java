package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simple mock payment gateway service (Stripe-like API)
 * This will be replaced with actual payment gateway integration later
 */
@Service
@Slf4j
public class PaymentGatewayService {

    /**
     * Create a payment intent (mock implementation)
     */
    public PaymentGatewayResponse createPaymentIntent(String orderId, String userId, BigDecimal amount, String currency) {
        log.info("Creating payment intent for order: {}, amount: {} {}", orderId, amount, currency);

        // Simulate processing time
        simulateProcessing();

        // Generate mock payment intent ID and client secret
        String paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "");

        return PaymentGatewayResponse.builder()
                .success(true)
                .paymentIntentId(paymentIntentId)
                .clientSecret(clientSecret)
                .status("PENDING")
                .build();
    }

    /**
     * Confirm a payment (mock implementation)
     */
    public PaymentGatewayResponse confirmPayment(String paymentIntentId, String paymentMethodId) {
        log.info("Confirming payment for intent: {}, method: {}", paymentIntentId, paymentMethodId);

        // Simulate processing time
        simulateProcessing();

        // 90% success rate for simulation
        boolean success = Math.random() < 0.9;

        if (success) {
            String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "");
            return PaymentGatewayResponse.builder()
                    .success(true)
                    .paymentIntentId(paymentIntentId)
                    .transactionId(transactionId)
                    .status("COMPLETED")
                    .build();
        } else {
            return PaymentGatewayResponse.builder()
                    .success(false)
                    .paymentIntentId(paymentIntentId)
                    .status("FAILED")
                    .errorMessage("Payment declined by bank")
                    .build();
        }
    }

    /**
     * Refund a payment (mock implementation)
     */
    public PaymentGatewayResponse refundPayment(String paymentIntentId, BigDecimal amount, String reason) {
        log.info("Refunding payment intent: {}, amount: {}, reason: {}", paymentIntentId, amount, reason);

        // Simulate processing time
        simulateProcessing();

        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "");

        return PaymentGatewayResponse.builder()
                .success(true)
                .paymentIntentId(paymentIntentId)
                .transactionId(refundId)
                .status("REFUNDED")
                .build();
    }

    /**
     * Simulate processing time (for realistic behavior)
     */
    private void simulateProcessing() {
        try {
            Thread.sleep(100 + (long) (Math.random() * 200)); // 100-300ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
