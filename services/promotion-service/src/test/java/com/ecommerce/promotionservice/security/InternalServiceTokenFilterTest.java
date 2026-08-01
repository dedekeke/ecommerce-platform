package com.ecommerce.promotionservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("InternalServiceTokenFilter Tests")
class InternalServiceTokenFilterTest {

    private static final String TOKEN = "s3cret-internal-token";

    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication authenticateWith(String configuredToken, String headerValue) throws Exception {
        if (headerValue != null) {
            request.addHeader(InternalServiceTokenFilter.HEADER_NAME, headerValue);
        }
        new InternalServiceTokenFilter(configuredToken).doFilter(request, response, chain);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("should_grantInternalServiceAuthority_when_tokenMatches")
    void should_grantInternalServiceAuthority_when_tokenMatches() throws Exception {
        Authentication authentication = authenticateWith(TOKEN, TOKEN);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(InternalServiceTokenFilter.INTERNAL_SERVICE_AUTHORITY);
    }

    @Test
    @DisplayName("should_continueChain_when_tokenMatches")
    void should_continueChain_when_tokenMatches() throws Exception {
        authenticateWith(TOKEN, TOKEN);

        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest(name = "header=\"{0}\"")
    @ValueSource(strings = {"wrong-token", "s3cret-internal-toke", "s3cret-internal-tokenX", ""})
    @DisplayName("should_notAuthenticate_when_tokenDoesNotMatch")
    void should_notAuthenticate_when_tokenDoesNotMatch(String headerValue) throws Exception {
        assertThat(authenticateWith(TOKEN, headerValue)).isNull();
    }

    @Test
    @DisplayName("should_notAuthenticate_when_headerAbsent")
    void should_notAuthenticate_when_headerAbsent() throws Exception {
        assertThat(authenticateWith(TOKEN, null)).isNull();
    }

    /**
     * A blank configured token must never authenticate a blank/absent header —
     * otherwise a misconfigured deployment would silently re-open /apply.
     */
    @ParameterizedTest(name = "configured=\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("should_disableAuthentication_when_configuredTokenBlank")
    void should_disableAuthentication_when_configuredTokenBlank(String configuredToken) throws Exception {
        assertThat(authenticateWith(configuredToken, "")).isNull();
    }

    /**
     * The filter sits AFTER the bearer-token filter, so a user JWT may already
     * be in the context. A valid service token must still win, so the guest
     * path (no JWT) and the authenticated path (user JWT that lacks any service
     * scope) both reach /apply with the same authority.
     */
    @Test
    @DisplayName("should_overrideExistingJwtAuthentication_when_tokenMatches")
    void should_overrideExistingJwtAuthentication_when_tokenMatches() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_read:promotions"))));

        Authentication authentication = authenticateWith(TOKEN, TOKEN);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(InternalServiceTokenFilter.INTERNAL_SERVICE_AUTHORITY);
    }

    @Test
    @DisplayName("should_preserveExistingAuthentication_when_tokenAbsent")
    void should_preserveExistingAuthentication_when_tokenAbsent() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        JwtAuthenticationToken existing = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(existing);

        assertThat(authenticateWith(TOKEN, null)).isSameAs(existing);
    }
}
