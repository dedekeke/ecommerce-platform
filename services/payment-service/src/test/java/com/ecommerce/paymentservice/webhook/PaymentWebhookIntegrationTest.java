package com.ecommerce.paymentservice.webhook;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentMethod;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.outbox.OutboxEvent;
import com.ecommerce.paymentservice.outbox.OutboxRepository;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context integration test for the Stripe webhook, with the REAL Spring
 * Security filter chain active ({@code security.enabled=true}). Proves:
 * <ul>
 *   <li>the endpoint is JWT-exempt (accepted with NO Authorization header) yet
 *       still authenticated by the Stripe signature — the SecurityConfig change;</li>
 *   <li>a valid signature reconciles the payment to COMPLETED and records a
 *       PAYMENT_COMPLETED outbox event (the payment→order path);</li>
 *   <li>an invalid signature is rejected 400 and changes no state;</li>
 *   <li>a duplicate delivery of the same event id applies exactly once.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "grpc.server.port=-1",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "stripe.webhook-secret=whsec_integration_secret"
})
@DisplayName("Payment Stripe Webhook Integration Tests")
class PaymentWebhookIntegrationTest {

    private static final String SECRET = "whsec_integration_secret";
    private static final String INTENT_ID = "pi_integration_1";
    private static final String ORDER_ID = "order-int-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedStripeEventRepository processedEventRepository;

    // Stub the decoder so the resource server starts without contacting Auth0. No bearer token is
    // ever sent to the webhook, which is the whole point of the JWT-exemption assertion.
    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // deleteAllInBatch (bulk JPQL DELETE) rather than deleteAll: ProcessedStripeEvent is
        // Persistable with isNew()==true, so the per-entity deleteAll would skip it as "new".
        outboxRepository.deleteAllInBatch();
        processedEventRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        paymentRepository.save(Payment.builder()
                .orderId(ORDER_ID)
                .userId("auth0|user-1")
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .method(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .paymentIntentId(INTENT_ID)
                .clientSecret("secret_abc")
                .build());
    }

    @Test
    @DisplayName("should accept an unauthenticated but signed webhook and reconcile the payment to COMPLETED")
    void should_reconcileToCompleted_when_signedSucceededWebhook() throws Exception {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_int_1", INTENT_ID, "ch_int_1");
        String signature = StripeWebhookTestSupport.signature(payload, SECRET);

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        Payment reloaded = paymentRepository.findByPaymentIntentId(INTENT_ID).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloaded.getTransactionId()).isEqualTo("ch_int_1");
        assertThat(completedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("should reject an invalid signature with 400 and leave the payment PENDING")
    void should_reject_when_signatureInvalid() throws Exception {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_int_2", INTENT_ID, "ch_int_2");
        String badSignature = StripeWebhookTestSupport.signature(payload, "whsec_wrong");

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", badSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        Payment reloaded = paymentRepository.findByPaymentIntentId(INTENT_ID).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(completedEvents()).isEmpty();
    }

    @Test
    @DisplayName("should apply a duplicated event id exactly once")
    void should_applyOnce_when_duplicateDelivery() throws Exception {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_int_3", INTENT_ID, "ch_int_3");
        String signature = StripeWebhookTestSupport.signature(payload, SECRET);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/payments/webhook")
                            .header("Stripe-Signature", signature)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());
        }

        assertThat(paymentRepository.findByPaymentIntentId(INTENT_ID).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completedEvents()).hasSize(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    private List<OutboxEvent> completedEvents() {
        return outboxRepository.findAll().stream()
                .filter(e -> "PAYMENT_COMPLETED".equals(e.getEventType()))
                .toList();
    }
}
