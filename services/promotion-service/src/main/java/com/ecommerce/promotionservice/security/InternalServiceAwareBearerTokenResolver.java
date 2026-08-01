package com.ecommerce.promotionservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Suppresses bearer-token processing for calls that present a valid internal
 * service token.
 *
 * <p>Why this exists: {@code BearerTokenAuthenticationFilter} rejects a request
 * with 401 as soon as an {@code Authorization: Bearer ...} header fails to
 * decode — before any later filter runs. A service-to-service call that happened
 * to carry a stale/expired user JWT (e.g. some future interceptor propagating the
 * inbound token) would therefore be 401'd even though it presents a perfectly
 * valid SERVICE credential. Returning {@code null} here makes the bearer filter a
 * no-op for those requests, so
 * {@link InternalServiceTokenFilter} decides the outcome and the service
 * credential always wins.
 *
 * <p>This does not weaken anything: the suppression only applies when the
 * constant-time service-token check has already passed, i.e. the caller has
 * proven it is a trusted platform service. A caller WITHOUT a valid service
 * token keeps the standard bearer semantics, invalid-token 401 included.
 */
@Slf4j
@RequiredArgsConstructor
public class InternalServiceAwareBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver delegate;
    private final InternalServiceTokenAuthenticator authenticator;

    public InternalServiceAwareBearerTokenResolver(InternalServiceTokenAuthenticator authenticator) {
        this(new DefaultBearerTokenResolver(), authenticator);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (authenticator.isTrustedServiceCall(request)) {
            log.debug("Ignoring Authorization header on trusted service call to {}", request.getRequestURI());
            return null;
        }
        return delegate.resolve(request);
    }
}
