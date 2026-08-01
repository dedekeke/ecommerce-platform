package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.webhook.PaymentWebhookService;
import com.ecommerce.paymentservice.webhook.WebhookVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for the Stripe webhook endpoint. Verifies the raw body
 * + signature header are passed through, 200 on success (so Stripe stops
 * retrying), and 400 on a verification failure.
 */
@DisplayName("PaymentWebhookController API Tests")
class PaymentWebhookControllerTest {

    private MockMvc mockMvc;
    private PaymentWebhookService webhookService;

    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";
    private static final String SIG = "t=123,v1=deadbeef";

    @BeforeEach
    void setUp() {
        webhookService = Mockito.mock(PaymentWebhookService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentWebhookController(webhookService))
                .setControllerAdvice(new PaymentApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("should return 200 and forward raw body + signature when the webhook is accepted")
    void should_return200_when_webhookAccepted() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", SIG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        verify(webhookService).handle(eq(PAYLOAD), eq(SIG));
    }

    @Test
    @DisplayName("should return 400 when signature verification fails")
    void should_return400_when_signatureInvalid() throws Exception {
        doThrow(new WebhookVerificationException("Invalid Stripe webhook signature"))
                .when(webhookService).handle(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should still reach the service when the Stripe-Signature header is absent")
    void should_forwardNullSignature_when_headerAbsent() throws Exception {
        // The verifier (inside the service) rejects a null signature; the controller
        // must not pre-empt that by 400-ing on a missing header binding.
        doThrow(new WebhookVerificationException("Missing Stripe-Signature header"))
                .when(webhookService).handle(any(), any());

        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(webhookService).handle(eq(PAYLOAD), eq(null));
    }
}
