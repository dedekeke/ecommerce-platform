package com.ecommerce.paymentservice.config;

import com.stripe.net.RequestOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StripeRequestOptionsFactory Unit Tests")
class StripeRequestOptionsFactoryTest {

    private StripeRequestOptionsFactory factory(String key, String base) {
        StripeProperties props = new StripeProperties();
        props.setSecretKey(key);
        props.setApiBase(base);
        return new StripeRequestOptionsFactory(props);
    }

    @Test
    @DisplayName("should build request options carrying the configured api key")
    void should_buildOptionsWithApiKey() {
        StripeRequestOptionsFactory factory = factory("sk_test_abc", "http://localhost:1234");
        factory.validateConfig();

        RequestOptions options = factory.build();

        assertThat(options.getApiKey()).isEqualTo("sk_test_abc");
        assertThat(options.getBaseUrl()).isEqualTo("http://localhost:1234");
    }

    @Test
    @DisplayName("should omit base url when not configured")
    void should_omitBaseUrl_when_notConfigured() {
        StripeRequestOptionsFactory factory = factory("sk_test_abc", "");
        factory.validateConfig();

        assertThat(factory.build().getBaseUrl()).isNull();
    }

    @Test
    @DisplayName("should attach the idempotency key when one is supplied")
    void should_attachIdempotencyKey_when_supplied() {
        StripeRequestOptionsFactory factory = factory("sk_test_abc", "");

        RequestOptions options = factory.build("order-42");

        assertThat(options.getIdempotencyKey()).isEqualTo("order-42");
    }

    @Test
    @DisplayName("should omit the idempotency key when blank or null")
    void should_omitIdempotencyKey_when_blank() {
        StripeRequestOptionsFactory factory = factory("sk_test_abc", "");

        assertThat(factory.build("  ").getIdempotencyKey()).isNull();
        assertThat(factory.build().getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("should reject startup when the secret key is blank")
    void should_reject_when_secretKeyBlank() {
        StripeRequestOptionsFactory factory = factory("  ", "http://localhost:1");

        assertThatThrownBy(factory::validateConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }
}
