package com.ecommerce.promotionservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates trusted service-to-service callers by a shared secret carried in
 * the {@value #HEADER_NAME} header, granting
 * {@value #INTERNAL_SERVICE_AUTHORITY}.
 *
 * <p>Why a shared secret rather than an OAuth2 scope: the only caller of
 * {@code POST /api/promotions/apply} is order-service's
 * {@code OrderCreationSaga}, which runs on both the authenticated and the GUEST
 * checkout path. On the guest path there is no user JWT at all, and on the
 * authenticated path the user's JWT carries only end-user scopes — no
 * {@code internal:service} scope is provisioned in Auth0 yet (see the same note
 * on user-service's {@code GET /api/users/by-auth0/{sub}}). A user-JWT-derived
 * guard would therefore reject guest orders outright. A service credential that
 * belongs to the CALLER, not to the end user, is the only mechanism that treats
 * both paths identically.
 *
 * <p><b>Ordering invariant:</b> this filter runs BEFORE
 * {@code BearerTokenAuthenticationFilter}, and
 * {@link InternalServiceAwareBearerTokenResolver} makes that filter a no-op for
 * requests bearing a valid service token. Together they guarantee a valid
 * service credential authenticates the call even when the request also carries a
 * malformed, expired or otherwise unusable {@code Authorization: Bearer} header
 * — the bearer filter can never 401 the call out from under this one.
 */
@Slf4j
@RequiredArgsConstructor
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Service-Token";
    public static final String INTERNAL_SERVICE_AUTHORITY = "ROLE_INTERNAL_SERVICE";
    private static final String PRINCIPAL = "internal-service";

    private final InternalServiceTokenAuthenticator authenticator;

    public InternalServiceTokenFilter(String expectedToken) {
        this(new InternalServiceTokenAuthenticator(expectedToken));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (authenticator.isTrustedServiceCall(request)) {
            AbstractAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated internal service caller for {} {}",
                    request.getMethod(), request.getRequestURI());
        } else if (authenticator.hasRejectedToken(request)) {
            log.warn("Rejected {} header on {} {} — value does not match the configured service token",
                    HEADER_NAME, request.getMethod(), request.getRequestURI());
        }
        chain.doFilter(request, response);
    }
}
