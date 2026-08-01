package com.ecommerce.paymentservice.savedmethod;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the deterministic mock Stripe adapter (§3.9).
 */
@DisplayName("MockStripeAdapter")
class MockStripeAdapterTest {

    private final MockStripeAdapter adapter = new MockStripeAdapter();

    @Test
    @DisplayName("providerName_should_returnSTRIPE")
    void providerName_should_returnSTRIPE() {
        assertThat(adapter.providerName()).isEqualTo("STRIPE");
    }

    @Test
    @DisplayName("attach_should_returnDeterministicResult_for_givenToken")
    void attach_should_returnDeterministicResult_for_givenToken() {
        PaymentProviderAdapter.AttachResult first = adapter.attachPaymentMethod("user-1", "tok_test_4242");
        PaymentProviderAdapter.AttachResult second = adapter.attachPaymentMethod("user-2", "tok_test_4242");

        // Same token -> same provider id (and the same display fields).
        assertThat(first.providerId()).isEqualTo(second.providerId());
        assertThat(first.last4()).isEqualTo(second.last4());
        assertThat(first.brand()).isEqualTo(second.brand());
    }

    @ParameterizedTest(name = "token={0} -> brand={1}")
    @CsvSource({
        "tok_visa_4242, VISA",
        "tok_M_5555,    MASTERCARD",
        "tok_A_3782,    AMEX",
        "tok_d_6011,    DISCOVER",
        "tok_xyz_4242,  VISA"
    })
    @DisplayName("brand_should_beDerivedFromTokenSuffix")
    void brand_should_beDerivedFromTokenSuffix(String token, String expectedBrand) {
        PaymentProviderAdapter.AttachResult result = adapter.attachPaymentMethod("user-1", token);
        assertThat(result.brand()).isEqualTo(expectedBrand);
    }

    @Test
    @DisplayName("last4_should_extractDigitsFromTokenSuffix")
    void last4_should_extractDigitsFromTokenSuffix() {
        PaymentProviderAdapter.AttachResult result = adapter.attachPaymentMethod("user-1", "tok_visa_4242");
        assertThat(result.last4()).isEqualTo("4242");
    }

    @Test
    @DisplayName("expMonth_and_expYear_should_bePopulated")
    void expMonth_and_expYear_should_bePopulated() {
        PaymentProviderAdapter.AttachResult result = adapter.attachPaymentMethod("user-1", "tok_test_4242");
        assertThat(result.expMonth()).isBetween(1, 12);
        assertThat(result.expYear()).isGreaterThan(2025);
    }

    @Test
    @DisplayName("attach_should_throw_when_userIdBlank")
    void attach_should_throw_when_userIdBlank() {
        assertThatThrownBy(() -> adapter.attachPaymentMethod("", "tok_test_4242"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("attach_should_throw_when_tokenBlank")
    void attach_should_throw_when_tokenBlank() {
        assertThatThrownBy(() -> adapter.attachPaymentMethod("user-1", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("providerId_should_includeTokenForTraceability")
    void providerId_should_includeTokenForTraceability() {
        PaymentProviderAdapter.AttachResult result = adapter.attachPaymentMethod("user-1", "tok_visa_4242");
        assertThat(result.providerId()).contains("tok_visa_4242");
    }

    @Test
    @DisplayName("createSetupIntent_should_returnIdAndClientSecret")
    void createSetupIntent_should_returnIdAndClientSecret() {
        PaymentProviderAdapter.SetupIntentResult result = adapter.createSetupIntent("user-1");

        assertThat(result.setupIntentId()).isNotBlank();
        assertThat(result.clientSecret()).contains(result.setupIntentId());
    }

    @Test
    @DisplayName("createSetupIntent_should_throw_when_userBlank")
    void createSetupIntent_should_throw_when_userBlank() {
        assertThatThrownBy(() -> adapter.createSetupIntent(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retrieveSetupIntent_should_recoverOwningUser_andSucceededStatus")
    void retrieveSetupIntent_should_recoverOwningUser_andSucceededStatus() {
        PaymentProviderAdapter.SetupIntentResult created = adapter.createSetupIntent("user-42");

        PaymentProviderAdapter.SetupIntentDetails details =
            adapter.retrieveSetupIntent(created.setupIntentId());

        assertThat(details.status()).isEqualTo("succeeded");
        assertThat(details.userId()).isEqualTo("user-42");
        assertThat(details.paymentMethodId()).isNotBlank();
        assertThat(details.last4()).hasSize(4);
        assertThat(details.brand()).isNotBlank();
    }

    @Test
    @DisplayName("retrieveSetupIntent_should_throw_when_idBlank")
    void retrieveSetupIntent_should_throw_when_idBlank() {
        assertThatThrownBy(() -> adapter.retrieveSetupIntent(""))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
