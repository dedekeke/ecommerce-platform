package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for the checkout PaymentIntent REST surface. A standalone setup keeps
 * the test isolated from the service's JPA/Kafka/OAuth2 auto-configuration.
 */
@DisplayName("PaymentIntentController API Tests")
class PaymentIntentControllerTest {

    private MockMvc mockMvc;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = Mockito.mock(PaymentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentIntentController(paymentService))
                .setControllerAdvice(new PaymentApiExceptionHandler())
                // Resolves @AuthenticationPrincipal Jwt to null in this unauthenticated standalone
                // setup so the controller falls back to the body userId (security is covered separately).
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    private Payment payment() {
        return Payment.builder()
                .id(7L)
                .orderId("order-1")
                .userId("user-1")
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentIntentId("pi_123")
                .clientSecret("pi_123_secret_abc")
                .build();
    }

    @Test
    @DisplayName("should return 201 with client secret when request is valid")
    void should_return201WithClientSecret_when_requestValid() throws Exception {
        when(paymentService.createPaymentIntent(eq("order-1"), eq("user-1"), any(), eq("USD")))
                .thenReturn(payment());

        String body = """
                {"orderId":"order-1","userId":"user-1","amount":42.00,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(7))
                .andExpect(jsonPath("$.paymentIntentId").value("pi_123"))
                .andExpect(jsonPath("$.clientSecret").value("pi_123_secret_abc"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("should return 400 when orderId is missing")
    void should_return400_when_orderIdMissing() throws Exception {
        String body = """
                {"userId":"user-1","amount":42.00,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.orderId").exists());

        verify(paymentService, never()).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should return 400 when amount is below the minimum")
    void should_return400_when_amountBelowMinimum() throws Exception {
        String body = """
                {"orderId":"order-1","userId":"user-1","amount":0.10,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DisplayName("should return 400 when currency is not a 3-letter code")
    void should_return400_when_currencyInvalid() throws Exception {
        String body = """
                {"orderId":"order-1","userId":"user-1","amount":42.00,"currency":"DOLLAR"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    @DisplayName("should return 500 with a generic message that does not leak internals when service fails")
    void should_return500WithGenericMessage_when_serviceFails() throws Exception {
        when(paymentService.createPaymentIntent(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused: jdbc://internal-db:5432"));

        String body = """
                {"orderId":"order-1","userId":"user-1","amount":42.00,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred while processing the payment."));
    }

    @Test
    @DisplayName("should return 404 when the payment is not found")
    void should_return404_when_paymentNotFound() throws Exception {
        when(paymentService.createPaymentIntent(any(), any(), any(), any()))
                .thenThrow(new com.ecommerce.paymentservice.service.PaymentNotFoundException("Payment not found for intent: pi_x"));

        String body = """
                {"orderId":"order-1","userId":"user-1","amount":42.00,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment not found for intent: pi_x"));
    }

    @Test
    @DisplayName("should return 409 when the payment is in an invalid state")
    void should_return409_when_invalidState() throws Exception {
        when(paymentService.createPaymentIntent(any(), any(), any(), any()))
                .thenThrow(new com.ecommerce.paymentservice.service.InvalidPaymentStateException("Payment cannot be refunded. Current status: PENDING"));

        String body = """
                {"orderId":"order-1","userId":"user-1","amount":42.00,"currency":"USD"}""";

        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Payment cannot be refunded. Current status: PENDING"));
    }
}
