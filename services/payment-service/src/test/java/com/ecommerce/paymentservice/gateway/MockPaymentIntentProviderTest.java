package com.ecommerce.paymentservice.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockPaymentIntentProvider Unit Tests")
class MockPaymentIntentProviderTest {

    private final MockPaymentIntentProvider provider = new MockPaymentIntentProvider();

    @Test
    @DisplayName("should return a pending intent with client secret when creating payment intent")
    void should_returnPendingIntentWithClientSecret_when_creatingPaymentIntent() {
        PaymentGatewayResponse response = provider.createPaymentIntent(
                "order-1", "user-1", new BigDecimal("42.00"), "USD");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getPaymentIntentId()).startsWith("pi_");
        assertThat(response.getClientSecret()).contains("_secret_");
    }

    @Test
    @DisplayName("should echo the payment intent id when confirming payment")
    void should_echoPaymentIntentId_when_confirmingPayment() {
        PaymentGatewayResponse response = provider.confirmPayment("pi_123", "pm_card_visa");

        assertThat(response.getPaymentIntentId()).isEqualTo("pi_123");
        assertThat(response.getStatus()).isIn("COMPLETED", "FAILED");
    }

    @Test
    @DisplayName("should set a transaction id when confirm succeeds")
    void should_setTransactionId_when_confirmSucceeds() {
        PaymentGatewayResponse success = null;
        for (int i = 0; i < 200 && success == null; i++) {
            PaymentGatewayResponse r = provider.confirmPayment("pi_123", "pm_card_visa");
            if (r.isSuccess()) {
                success = r;
            }
        }
        assertThat(success).as("expected at least one success across 200 attempts").isNotNull();
        assertThat(success.getStatus()).isEqualTo("COMPLETED");
        assertThat(success.getTransactionId()).startsWith("txn_");
    }

    @Test
    @DisplayName("should populate an error message when confirm fails")
    void should_populateErrorMessage_when_confirmFails() {
        PaymentGatewayResponse failure = null;
        for (int i = 0; i < 200 && failure == null; i++) {
            PaymentGatewayResponse r = provider.confirmPayment("pi_123", "pm_card_visa");
            if (!r.isSuccess()) {
                failure = r;
            }
        }
        assertThat(failure).as("expected at least one failure across 200 attempts").isNotNull();
        assertThat(failure.getStatus()).isEqualTo("FAILED");
        assertThat(failure.getErrorMessage()).isEqualTo("Payment declined by bank");
    }

    @Test
    @DisplayName("should return refunded status with a refund id when refunding")
    void should_returnRefunded_when_refunding() {
        PaymentGatewayResponse response = provider.refundPayment("pi_123", new BigDecimal("10.00"), "requested_by_customer");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("REFUNDED");
        assertThat(response.getPaymentIntentId()).isEqualTo("pi_123");
        assertThat(response.getTransactionId()).startsWith("re_");
    }

    @Test
    @DisplayName("should report gateway unavailable from each Resilience4j fallback")
    void should_reportUnavailable_when_fallbacksInvoked() {
        RuntimeException cause = new RuntimeException("circuit open");

        assertThat(provider.createPaymentIntentFallback("o", "u", new BigDecimal("1.00"), "USD", cause).getStatus())
                .isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(provider.confirmPaymentFallback("pi_123", "pm_card_visa", cause).getStatus())
                .isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(provider.refundPaymentFallback("pi_123", new BigDecimal("1.00"), "reason", cause).getStatus())
                .isEqualTo("REFUND_PENDING");
    }
}
