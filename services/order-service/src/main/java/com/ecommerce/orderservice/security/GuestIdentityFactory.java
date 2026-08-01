package com.ecommerce.orderservice.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives the stable, opaque owner identity for a guest checkout from the
 * guest's email address.
 *
 * <p><b>Why derive from email (not a random UUID)?</b> The order-creation saga
 * fetches the cart from cart-service keyed by the owner id. A guest identity must
 * therefore be <i>stable</i> so the saga resolves the SAME cart the browser built
 * under that identity — a fresh per-request UUID would always resolve an empty
 * cart. Email is the natural stable key and doubles as the claim key.</p>
 *
 * <p><b>Format:</b> {@code "guest:" + sha256hex(normalize(email))}.
 * <ul>
 *   <li><b>guest: prefix</b> — marks the value in {@code orders.user_id} and can
 *       never collide with an Auth0 {@code sub}, so guest and authenticated
 *       owners share a column without ambiguity.</li>
 *   <li><b>sha256 (not raw email)</b> — keeps PII out of the userId that flows to
 *       inventory/payment as the correlation id; the raw email is persisted once,
 *       in {@code orders.guest_email}, for the claim path.</li>
 *   <li><b>normalize (trim + lowercase)</b> — the same person typing
 *       {@code  Foo@Bar.com } and {@code foo@bar.com} maps to one identity, so
 *       their cart and idempotency reservations line up.</li>
 * </ul>
 *
 * <p>The identity is intentionally guessable from the email; it grants no
 * access to guest data (the guest endpoint only <i>creates</i> an order the
 * caller must then pay for) and abuse is bounded by the gateway rate limit.</p>
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
