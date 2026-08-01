package com.ecommerce.promotionservice.security;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time matcher for the shared inter-service secret carried in the
 * {@value InternalServiceTokenFilter#HEADER_NAME} header.
 *
 * <p>Shared by {@link InternalServiceTokenFilter} (which authenticates the
 * caller) and {@link InternalServiceAwareBearerTokenResolver} (which suppresses
 * bearer-token processing for such calls), so both agree on exactly one
 * definition of "this request presents a valid service credential".
 *
 * <p>A blank configured secret disables the credential entirely: no presented
 * value can ever match, so the guarded endpoints stay closed rather than
 * silently re-opening on a misconfiguration.
 */
public class InternalServiceTokenAuthenticator {

    private final byte[] expectedToken;

    public InternalServiceTokenAuthenticator(String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank()
                ? null
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return {@code true} when the request carries the configured service token.
     */
    public boolean isTrustedServiceCall(HttpServletRequest request) {
        return matches(request.getHeader(InternalServiceTokenFilter.HEADER_NAME));
    }

    /**
     * @return {@code true} when the request carries a
     *         {@value InternalServiceTokenFilter#HEADER_NAME} header that does
     *         NOT match — used only for logging a rejected attempt.
     */
    public boolean hasRejectedToken(HttpServletRequest request) {
        String presented = request.getHeader(InternalServiceTokenFilter.HEADER_NAME);
        return presented != null && !matches(presented);
    }

    private boolean matches(String presented) {
        if (expectedToken == null || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8));
    }
}
