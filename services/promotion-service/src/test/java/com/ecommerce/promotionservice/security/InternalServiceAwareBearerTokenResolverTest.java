package com.ecommerce.promotionservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InternalServiceAwareBearerTokenResolver Tests")
class InternalServiceAwareBearerTokenResolverTest {

    private static final String TOKEN = "s3cret-internal-token";

    private final InternalServiceAwareBearerTokenResolver resolver =
            new InternalServiceAwareBearerTokenResolver(new InternalServiceTokenAuthenticator(TOKEN));

    private MockHttpServletRequest request(String serviceToken, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (serviceToken != null) {
            request.addHeader(InternalServiceTokenFilter.HEADER_NAME, serviceToken);
        }
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }

    /**
     * The whole point: a trusted service call must never be 401'd by the bearer
     * filter over a stale/garbage Authorization header it did not need.
     */
    @Test
    @DisplayName("should_suppressBearerToken_when_serviceTokenValid")
    void should_suppressBearerToken_when_serviceTokenValid() {
        assertThat(resolver.resolve(request(TOKEN, "Bearer garbage"))).isNull();
    }

    @Test
    @DisplayName("should_resolveBearerToken_when_serviceTokenAbsent")
    void should_resolveBearerToken_when_serviceTokenAbsent() {
        assertThat(resolver.resolve(request(null, "Bearer user-jwt"))).isEqualTo("user-jwt");
    }

    @Test
    @DisplayName("should_resolveBearerToken_when_serviceTokenWrong")
    void should_resolveBearerToken_when_serviceTokenWrong() {
        assertThat(resolver.resolve(request("wrong-token", "Bearer user-jwt"))).isEqualTo("user-jwt");
    }

    @Test
    @DisplayName("should_returnNull_when_noAuthorizationHeader")
    void should_returnNull_when_noAuthorizationHeader() {
        assertThat(resolver.resolve(request(null, null))).isNull();
    }

    /**
     * A blank configured secret disables the credential, so suppression must not
     * kick in for a caller sending an arbitrary header value.
     */
    @Test
    @DisplayName("should_resolveBearerToken_when_configuredSecretBlank")
    void should_resolveBearerToken_when_configuredSecretBlank() {
        InternalServiceAwareBearerTokenResolver blankResolver =
                new InternalServiceAwareBearerTokenResolver(new InternalServiceTokenAuthenticator(""));

        assertThat(blankResolver.resolve(request("anything", "Bearer user-jwt"))).isEqualTo("user-jwt");
    }
}
