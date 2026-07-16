package com.ecommerce.paymentservice.savedmethod;

import com.ecommerce.paymentservice.config.StripeProperties;
import com.ecommerce.paymentservice.config.StripeRequestOptionsFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test exercising the real Stripe Java SDK against a WireMock-stubbed Stripe API.
 * No real Stripe credentials or network calls are involved.
 */
@DisplayName("StripePaymentProviderAdapter (savedmethod) Integration Tests")
class StripePaymentProviderAdapterTest {

    private WireMockServer wireMock;
    private StripePaymentProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        StripeProperties props = new StripeProperties();
        props.setSecretKey("sk_test_dummy");
        props.setApiBase("http://localhost:" + wireMock.port());

        StripeRequestOptionsFactory factory = new StripeRequestOptionsFactory(props);
        adapter = new StripePaymentProviderAdapter(factory);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    @DisplayName("providerName should return STRIPE")
    void providerName_should_returnStripe() {
        assertThat(adapter.providerName()).isEqualTo("STRIPE");
    }

    @Test
    @DisplayName("should map Stripe card metadata into an AttachResult")
    void should_mapCardMetadata_when_attaching() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payment_methods/pm_test_123"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pm_test_123","object":"payment_method","type":"card",
                                 "card":{"brand":"visa","last4":"4242","exp_month":12,"exp_year":2030}}""")));

        PaymentProviderAdapter.AttachResult result = adapter.attachPaymentMethod("user-1", "pm_test_123");

        assertThat(result.providerId()).isEqualTo("pm_test_123");
        assertThat(result.last4()).isEqualTo("4242");
        assertThat(result.brand()).isEqualTo("VISA");
        assertThat(result.expMonth()).isEqualTo(12);
        assertThat(result.expYear()).isEqualTo(2030);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/v1/payment_methods/pm_test_123"))
                .withHeader("Authorization", equalTo("Bearer sk_test_dummy")));
    }

    @Test
    @DisplayName("should throw when the Stripe payment method is not a card")
    void should_throw_when_notACard() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payment_methods/pm_ideal"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"pm_ideal","object":"payment_method","type":"ideal"}""")));

        assertThatThrownBy(() -> adapter.attachPaymentMethod("user-1", "pm_ideal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a card");
    }

    @Test
    @DisplayName("should wrap Stripe errors in a PaymentProviderException")
    void should_wrapStripeError_when_retrieveFails() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payment_methods/pm_missing"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"type":"invalid_request_error","message":"No such payment method"}}""")));

        assertThatThrownBy(() -> adapter.attachPaymentMethod("user-1", "pm_missing"))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    @DisplayName("createSetupIntent should create a SetupIntent carrying the userId metadata and return its client secret")
    void should_createSetupIntent_withUserMetadata() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/setup_intents"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"seti_123","object":"setup_intent","status":"requires_payment_method",
                                 "client_secret":"seti_123_secret_abc","usage":"off_session"}""")));

        PaymentProviderAdapter.SetupIntentResult result = adapter.createSetupIntent("user-1");

        assertThat(result.setupIntentId()).isEqualTo("seti_123");
        assertThat(result.clientSecret()).isEqualTo("seti_123_secret_abc");
        // The owning user is stamped on metadata so confirm/webhook can authorize against it.
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/setup_intents"))
                .withRequestBody(containing("userId"))
                .withRequestBody(containing("user-1"))
                .withHeader("Authorization", equalTo("Bearer sk_test_dummy")));
    }

    @Test
    @DisplayName("retrieveSetupIntent should return status, metadata userId, and expanded card fields")
    void should_retrieveSetupIntent_withExpandedCard() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/setup_intents/seti_123"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"seti_123","object":"setup_intent","status":"succeeded",
                                 "metadata":{"userId":"user-1"},
                                 "payment_method":{"id":"pm_1","object":"payment_method","type":"card",
                                   "card":{"brand":"visa","last4":"4242","exp_month":12,"exp_year":2031}}}""")));

        PaymentProviderAdapter.SetupIntentDetails details = adapter.retrieveSetupIntent("seti_123");

        assertThat(details.status()).isEqualTo("succeeded");
        assertThat(details.userId()).isEqualTo("user-1");
        assertThat(details.paymentMethodId()).isEqualTo("pm_1");
        assertThat(details.last4()).isEqualTo("4242");
        assertThat(details.brand()).isEqualTo("VISA");
        assertThat(details.expMonth()).isEqualTo(12);
        assertThat(details.expYear()).isEqualTo(2031);
    }

    @Test
    @DisplayName("should wrap Stripe errors from setup intent retrieval in a PaymentProviderException")
    void should_wrapStripeError_when_setupIntentRetrieveFails() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/setup_intents/seti_missing"))
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":{"type":"invalid_request_error","message":"No such setupintent"}}""")));

        assertThatThrownBy(() -> adapter.retrieveSetupIntent("seti_missing"))
                .isInstanceOf(PaymentProviderException.class);
    }

    @Test
    @DisplayName("should reject a blank userId")
    void should_reject_when_userIdBlank() {
        assertThatThrownBy(() -> adapter.attachPaymentMethod("  ", "pm_test_123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject a blank token")
    void should_reject_when_tokenBlank() {
        assertThatThrownBy(() -> adapter.attachPaymentMethod("user-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
