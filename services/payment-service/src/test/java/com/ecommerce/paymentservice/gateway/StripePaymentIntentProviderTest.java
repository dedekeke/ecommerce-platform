package com.ecommerce.paymentservice.gateway;

import com.ecommerce.paymentservice.config.StripeProperties;
import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test exercising the real Stripe Java SDK against a WireMock-stubbed Stripe API.
 * No real Stripe credentials or network calls are involved; the SDK base URL is redirected to
 * the local WireMock server via {@code stripe.api-base}.
 */
@DisplayName("StripePaymentIntentProvider Integration Tests (WireMock)")
class StripePaymentIntentProviderTest {

    private WireMockServer wireMock;
    private StripePaymentIntentProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        StripeProperties props = new StripeProperties();
        props.setSecretKey("sk_test_dummy");
        props.setApiBase("http://localhost:" + wireMock.port());

        StripeRequestOptionsFactory factory = new StripeRequestOptionsFactory(props);
        provider = new StripePaymentIntentProvider(factory);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("should return client secret and amount in minor units when creating a payment intent")
    void should_returnClientSecret_when_creatingPaymentIntent() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pi_test_123","object":"payment_intent","status":"requires_payment_method",
                                 "client_secret":"pi_test_123_secret_abc","amount":4200,"currency":"usd"}""")));

        PaymentGatewayResponse response = provider.createPaymentIntent(
                "order-9", "user-9", new BigDecimal("42.00"), "USD");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPaymentIntentId()).isEqualTo("pi_test_123");
        assertThat(response.getClientSecret()).isEqualTo("pi_test_123_secret_abc");
        assertThat(response.getStatus()).isEqualTo("PENDING");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/payment_intents"))
                .withHeader("Authorization", equalTo("Bearer sk_test_dummy"))
                .withRequestBody(matching(".*amount=4200.*"))
                .withRequestBody(matching(".*currency=usd.*"))
                .withRequestBody(matching(".*metadata\\[orderId]=order-9.*")));
    }

    @Test
    @DisplayName("should mark failure when Stripe returns an error on create")
    void should_returnFailure_when_stripeReturnsErrorOnCreate() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payment_intents"))
                .willReturn(aResponse().withStatus(402).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"type":"card_error","code":"card_declined","message":"Your card was declined."}}""")));

        PaymentGatewayResponse response = provider.createPaymentIntent(
                "order-x", "user-x", new BigDecimal("5.00"), "usd");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getErrorMessage()).contains("declined");
    }

    @Test
    @DisplayName("should return completed status with transaction id when confirm succeeds")
    void should_returnCompleted_when_confirmSucceeds() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payment_intents/pi_test_123"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pi_test_123","status":"requires_confirmation","client_secret":"s"}""")));
        wireMock.stubFor(post(urlPathEqualTo("/v1/payment_intents/pi_test_123/confirm"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pi_test_123","status":"succeeded","latest_charge":"ch_test_456"}""")));

        PaymentGatewayResponse response = provider.confirmPayment("pi_test_123", "pm_card_visa");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTransactionId()).isEqualTo("ch_test_456");
    }

    @Test
    @DisplayName("should return non-success when confirm leaves intent requiring action")
    void should_returnNonSuccess_when_confirmRequiresAction() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payment_intents/pi_test_123"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pi_test_123","status":"requires_confirmation","client_secret":"s"}""")));
        wireMock.stubFor(post(urlPathEqualTo("/v1/payment_intents/pi_test_123/confirm"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pi_test_123","status":"requires_action"}""")));

        PaymentGatewayResponse response = provider.confirmPayment("pi_test_123", "pm_card_visa");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getErrorMessage()).contains("requires_action");
    }

    @Test
    @DisplayName("should return refunded status with refund id when refunding")
    void should_returnRefunded_when_refunding() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/refunds"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"re_test_789","object":"refund","status":"succeeded","payment_intent":"pi_test_123"}""")));

        PaymentGatewayResponse response = provider.refundPayment("pi_test_123", new BigDecimal("10.00"), "requested_by_customer");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("REFUNDED");
        assertThat(response.getTransactionId()).isEqualTo("re_test_789");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/refunds"))
                .withRequestBody(matching(".*amount=1000.*"))
                .withRequestBody(matching(".*payment_intent=pi_test_123.*")));
    }

    @Test
    @DisplayName("should report gateway unavailable from each Resilience4j fallback")
    void should_reportUnavailable_when_fallbacksInvoked() {
        RuntimeException cause = new RuntimeException("circuit open");

        assertThat(provider.createPaymentIntentFallback("o", "u", new BigDecimal("1.00"), "usd", cause).getStatus())
                .isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(provider.confirmPaymentFallback("pi_1", "pm_1", cause).getStatus())
                .isEqualTo("GATEWAY_UNAVAILABLE");
        assertThat(provider.refundPaymentFallback("pi_1", new BigDecimal("1.00"), "r", cause).getStatus())
                .isEqualTo("REFUND_PENDING");
    }
}
