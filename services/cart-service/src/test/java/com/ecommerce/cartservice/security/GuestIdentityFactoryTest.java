package com.ecommerce.cartservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GuestIdentityFactory (cart-service) Unit Tests")
class GuestIdentityFactoryTest {

    private final GuestIdentityFactory factory = new GuestIdentityFactory();

    /**
     * CROSS-SERVICE CONTRACT PIN. This exact email → identity vector is shared
     * with order-service (whose OrderServiceTest claims guest orders under
     * "guest@example.com"). If this assertion ever fails, cart-service and
     * order-service have drifted and guest checkout will resolve an empty cart.
     * The expected value is sha256hex("guest@example.com") with the guest: prefix.
     */
    @Test
    @DisplayName("Should derive the exact cross-service identity for a known email")
    void should_deriveExactIdentity_when_knownEmail() {
        String id = factory.guestId("guest@example.com");

        assertThat(id).isEqualTo(
                "guest:513935c4d2db2d2d984dff1d68397f6e2ac8c4e5c48c92bd98e02bdc90b7aefe");
    }

    @Test
    @DisplayName("Should carry the guest: prefix so it can never collide with an Auth0 sub")
    void should_prefixWithGuest_when_derivingIdentity() {
        assertThat(factory.guestId("shopper@example.com")).startsWith("guest:");
    }

    @ParameterizedTest
    @DisplayName("Should normalise (trim + lowercase) to one identity")
    @ValueSource(strings = {"foo@bar.com", "FOO@BAR.COM", "  Foo@Bar.com  ", "foo@Bar.COM"})
    void should_mapToSameIdentity_when_emailVariesInCaseOrWhitespace(String variant) {
        assertThat(factory.guestId(variant)).isEqualTo(factory.guestId("foo@bar.com"));
    }

    @Test
    @DisplayName("Should produce different identities for different emails")
    void should_produceDistinctIdentities_when_emailsDiffer() {
        assertThat(factory.guestId("a@example.com"))
                .isNotEqualTo(factory.guestId("b@example.com"));
    }

    @ParameterizedTest
    @DisplayName("Should reject a blank email")
    @ValueSource(strings = {"", "   ", "\t"})
    void should_throw_when_emailBlank(String blank) {
        assertThatThrownBy(() -> factory.guestId(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Should reject a null email")
    void should_throw_when_emailNull() {
        assertThatThrownBy(() -> factory.guestId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
