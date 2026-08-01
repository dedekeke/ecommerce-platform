package com.ecommerce.paymentservice.webhook;

import com.ecommerce.paymentservice.config.StripeProperties;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the REAL Stripe signature check ({@code Webhook.constructEvent}) with
 * HMAC-signed payloads, so a regression in the verification wiring is caught
 * without a live Stripe account.
 */
@DisplayName("DefaultStripeWebhookVerifier Tests")
class DefaultStripeWebhookVerifierTest {

    private static final String SECRET = "whsec_test_secret_123";

    private DefaultStripeWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(SECRET);
        verifier = new DefaultStripeWebhookVerifier(properties);
    }

    @Test
    @DisplayName("should parse the event when the signature is valid")
    void should_parseEvent_when_signatureValid() {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1");
        String header = StripeWebhookTestSupport.signature(payload, SECRET);

        Event event = verifier.verifyAndParse(payload, header);

        assertThat(event.getId()).isEqualTo("evt_1");
        assertThat(event.getType()).isEqualTo("payment_intent.succeeded");
    }

    @Test
    @DisplayName("should reject when the payload was tampered after signing")
    void should_reject_when_payloadTampered() {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1");
        String header = StripeWebhookTestSupport.signature(payload, SECRET);
        String tampered = payload.replace("pi_1", "pi_evil");

        assertThatThrownBy(() -> verifier.verifyAndParse(tampered, header))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    @DisplayName("should reject when the signature was produced with a different secret")
    void should_reject_when_secretMismatch() {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1");
        String header = StripeWebhookTestSupport.signature(payload, "whsec_wrong_secret");

        assertThatThrownBy(() -> verifier.verifyAndParse(payload, header))
                .isInstanceOf(WebhookVerificationException.class);
    }

    @Test
    @DisplayName("should reject when the Stripe-Signature header is missing")
    void should_reject_when_signatureHeaderMissing() {
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1");

        assertThatThrownBy(() -> verifier.verifyAndParse(payload, null))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("Missing");
    }

    @Test
    @DisplayName("should reject when the webhook signing secret is not configured")
    void should_reject_when_secretNotConfigured() {
        StripeProperties empty = new StripeProperties();
        DefaultStripeWebhookVerifier unconfigured = new DefaultStripeWebhookVerifier(empty);
        String payload = StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1");
        String header = StripeWebhookTestSupport.signature(payload, SECRET);

        assertThatThrownBy(() -> unconfigured.verifyAndParse(payload, header))
                .isInstanceOf(WebhookVerificationException.class)
                .hasMessageContaining("not configured");
    }
}
