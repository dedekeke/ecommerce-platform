package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the security invariant that the checkout PaymentIntent response never serializes the
 * internal {@code failureReason} (raw gateway/Stripe detail) to clients.
 */
@DisplayName("PaymentIntentDtos.Response serialization")
class PaymentIntentResponseDtoTest {

    private static final String RAW_STRIPE_DETAIL =
            "card_declined: insufficient_funds [request-id: req_abc123]";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("should not serialize failureReason in the checkout response")
    void should_notSerializeFailureReason() throws Exception {
        Payment payment = Payment.builder()
                .id(7L)
                .orderId("order-1")
                .userId("auth0|user-1")
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(PaymentStatus.FAILED)
                .paymentIntentId("pi_123")
                .clientSecret("pi_123_secret_abc")
                .failureReason(RAW_STRIPE_DETAIL)
                .build();

        String json = mapper.writeValueAsString(PaymentIntentDtos.Response.from(payment));

        assertThat(json).doesNotContain("failureReason");
        assertThat(json).doesNotContain(RAW_STRIPE_DETAIL);
        assertThat(json).doesNotContain("req_abc123");
        assertThat(json).contains("pi_123");
    }
}
