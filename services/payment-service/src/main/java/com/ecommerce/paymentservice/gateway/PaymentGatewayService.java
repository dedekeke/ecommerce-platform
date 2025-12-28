package com.ecommerce.paymentservice.gateway;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment gateway service with Resilience4j protection.
 *
 * This mock implementation simulates a real payment gateway (Stripe-like API).
 * All external calls are protected by circuit breaker, retry, and bulkhead patterns.
 */
@Service
@Slf4j
public class PaymentGatewayService {

    /**
     * Create a payment intent (mock implementation).
     * Protected by circuit breaker with fallback for gateway failures.
     */
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "createPaymentIntentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
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
     * Confirm a payment (mock implementation).
     * Protected by circuit breaker with fallback for gateway failures.
     */
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "confirmPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
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
     * Refund a payment (mock implementation).
     * Protected by circuit breaker with fallback for gateway failures.
     */
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "refundPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
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

    // ==================== Fallback Methods ====================

    /**
     * Fallback for createPaymentIntent when payment gateway is unavailable.
     */
    private PaymentGatewayResponse createPaymentIntentFallback(String orderId, String userId,
            BigDecimal amount, String currency, Throwable t) {
        log.error("Payment gateway unavailable for creating payment intent. Order: {}, Error: {}",
            orderId, t.getMessage());

        return PaymentGatewayResponse.builder()
                .success(false)
                .status("GATEWAY_UNAVAILABLE")
                .errorMessage("Payment gateway is temporarily unavailable. Please try again later.")
                .build();
    }

    /**
     * Fallback for confirmPayment when payment gateway is unavailable.
     */
    private PaymentGatewayResponse confirmPaymentFallback(String paymentIntentId,
            String paymentMethodId, Throwable t) {
        log.error("Payment gateway unavailable for confirming payment. Intent: {}, Error: {}",
            paymentIntentId, t.getMessage());

        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("GATEWAY_UNAVAILABLE")
                .errorMessage("Payment gateway is temporarily unavailable. Your payment has not been processed.")
                .build();
    }

    /**
     * Fallback for refundPayment when payment gateway is unavailable.
     */
    private PaymentGatewayResponse refundPaymentFallback(String paymentIntentId,
            BigDecimal amount, String reason, Throwable t) {
        log.error("Payment gateway unavailable for refund. Intent: {}, Error: {}",
            paymentIntentId, t.getMessage());

        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("REFUND_PENDING")
                .errorMessage("Refund request queued. Will be processed when gateway is available.")
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
