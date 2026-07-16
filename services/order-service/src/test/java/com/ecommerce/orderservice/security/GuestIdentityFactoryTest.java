package com.ecommerce.orderservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestIdentityFactoryTest {

    private final GuestIdentityFactory factory = new GuestIdentityFactory();

    @Test
    void should_prefixWithGuest_when_derivingIdentity() {
        assertTrue(factory.guestId("buyer@example.com").startsWith(GuestIdentityFactory.GUEST_PREFIX));
    }

    @Test
    void should_beDeterministic_when_sameEmail() {
        assertEquals(factory.guestId("buyer@example.com"), factory.guestId("buyer@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"  Buyer@Example.com ", "buyer@example.com", "BUYER@EXAMPLE.COM"})
    void should_normalizeCaseAndWhitespace_when_derivingIdentity(String variant) {
        assertEquals(factory.guestId("buyer@example.com"), factory.guestId(variant));
    }

    @Test
    void should_notEmbedRawEmail_when_derivingIdentity() {
        // PII must not leak into the userId that flows to inventory/payment.
        assertTrue(factory.guestId("buyer@example.com").matches("guest:[0-9a-f]{64}"));
    }

    @Test
    void should_produceDistinctIdentities_when_differentEmails() {
        assertNotEquals(factory.guestId("a@example.com"), factory.guestId("b@example.com"));
    }

    @Test
    void should_normalizeConsistently_when_normalizeEmailCalled() {
        assertEquals("buyer@example.com", factory.normalizeEmail("  Buyer@Example.COM "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void should_reject_when_blankEmail(String blank) {
        assertThrows(IllegalArgumentException.class, () -> factory.normalizeEmail(blank));
    }

    @Test
    void should_matchConvenienceAndTwoStep_when_deriving() {
        String normalized = factory.normalizeEmail(" Buyer@Example.com ");
        assertAll(
            () -> assertEquals(factory.guestId(" Buyer@Example.com "),
                factory.guestIdForNormalizedEmail(normalized)),
            () -> assertEquals("buyer@example.com", normalized)
        );
    }
}
