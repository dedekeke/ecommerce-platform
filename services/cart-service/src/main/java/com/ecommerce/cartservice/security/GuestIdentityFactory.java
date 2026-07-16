package com.ecommerce.cartservice.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives the stable, opaque owner identity for an ANONYMOUS (guest) cart from
 * the guest's email address.
 *
 * <p><b>This is a deliberate mirror of order-service's {@code GuestIdentityFactory}
 * and MUST stay byte-for-byte compatible with it.</b> The order-creation saga
 * fetches the cart from cart-service keyed by the owner id
 * ({@code guest:sha256hex(normalize(email))}); if cart-service derived a different
 * identity, a guest's cart would never be found at checkout and the saga would
 * fail with EmptyCart. The cross-service contract is pinned by
 * {@code GuestIdentityFactoryTest} against a fixed email → hash vector shared with
 * order-service's own test.</p>
 *
 * <p><b>Format:</b> {@code "guest:" + sha256hex(normalize(email))}.
 * <ul>
 *   <li><b>guest: prefix</b> — marks the value in {@code carts.user_id}; it can
 *       never collide with an Auth0 {@code sub}, so guest and authenticated carts
 *       share the column without ambiguity.</li>
 *   <li><b>sha256 (not raw email)</b> — keeps PII out of the persisted owner id.</li>
 *   <li><b>normalize (trim + lowercase)</b> — {@code  Foo@Bar.com } and
 *       {@code foo@bar.com} map to one identity, so the cart the browser builds
 *       and the cart the saga reads at checkout line up.</li>
 * </ul>
 *
 * <p>The identity is intentionally guessable from the email; it grants no
 * elevated access (a guest can only mutate their own soft cart) and abuse is
 * bounded by the gateway's IP-keyed rate limit on the guest-cart routes.</p>
 */
@Component
public class GuestIdentityFactory {

    public static final String GUEST_PREFIX = "guest:";

    /** Normalize an email for use as both the guest identity seed and the claim key. */
    public String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Guest email must not be blank");
        }
        return email.trim().toLowerCase();
    }

    /** Resolve the stable guest owner identity for an already-normalized email. */
    public String guestIdForNormalizedEmail(String normalizedEmail) {
        return GUEST_PREFIX + sha256Hex(normalizedEmail);
    }

    /** Convenience: normalize then derive — for callers holding a raw email. */
    public String guestId(String email) {
        return guestIdForNormalizedEmail(normalizeEmail(email));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS on every JVM — unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
