package com.ecommerce.paymentservice.gateway;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock PaymentIntent gateway that simulates a Stripe-like API.
 *
 * <p>Active by default ({@code payment.provider=mock} or unset) so tests and local development
 * never need real Stripe credentials. All calls are protected by circuit breaker, retry, and
 * bulkhead patterns.</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentIntentProvider implements PaymentIntentProvider {

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "createPaymentIntentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse createPaymentIntent(String orderId, String userId, BigDecimal amount, String currency) {
        log.info("[mock] Creating payment intent for order: {}, amount: {} {}", orderId, amount, currency);

        simulateProcessing();

        String paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "");

        return PaymentGatewayResponse.builder()
                .success(true)
                .paymentIntentId(paymentIntentId)
                .clientSecret(clientSecret)
                .status("PENDING")
                .build();
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "confirmPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse confirmPayment(String paymentIntentId, String paymentMethodId) {
        log.info("[mock] Confirming payment for intent: {}, method: {}", paymentIntentId, paymentMethodId);

        simulateProcessing();

        boolean success = Math.random() < 0.9;

        if (success) {
            String transactionId = "txn_" + UUID.randomUUID().toString().replace("-", "");
            return PaymentGatewayResponse.builder()
                    .success(true)
                    .paymentIntentId(paymentIntentId)
                    .transactionId(transactionId)
                    .status("COMPLETED")
                    .build();
        }
        return PaymentGatewayResponse.builder()
                .success(false)
                .paymentIntentId(paymentIntentId)
                .status("FAILED")
                .errorMessage("Payment declined by bank")
                .build();
    }

    @Override
    @CircuitBreaker(name = "payment-gateway", fallbackMethod = "refundPaymentFallback")
    @Retry(name = "payment-gateway")
    @Bulkhead(name = "payment-gateway")
    public PaymentGatewayResponse refundPayment(String paymentIntentId, BigDecimal amount, String reason) {
        log.info("[mock] Refunding payment intent: {}, amount: {}, reason: {}", paymentIntentId, amount, reason);

        simulateProcessing();

        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "");

        return PaymentGatewayResponse.builder()
                .success(true)
                .paymentIntentId(paymentIntentId)
                .transactionId(refundId)
                .status("REFUNDED")
                .build();
    }

    PaymentGatewayResponse createPaymentIntentFallback(String orderId, String userId,
            BigDecimal amount, String currency, Throwable t) {
        log.error("Payment gateway unavailable for creating payment intent. Order: {}, Error: {}",
            orderId, t.getMessage());
        return PaymentGatewayResponse.builder()
                .success(false)
                .status("GATEWAY_UNAVAILABLE")
                .errorMessage("Payment gateway is temporarily unavailable. Please try again later.")
                .build();
    }

    PaymentGatewayResponse confirmPaymentFallback(String paymentIntentId,
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

    PaymentGatewayResponse refundPaymentFallback(String paymentIntentId,
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

    private void simulateProcessing() {
        try {
            Thread.sleep(100 + (long) (Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
