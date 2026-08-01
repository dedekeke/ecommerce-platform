package com.ecommerce.promotionservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * <p>The filter is installed AFTER the bearer-token filter so a valid service
 * token wins over whatever end-user authentication happens to be present.
 * Comparison is constant-time; a blank configured secret disables the filter
 * entirely (it can then never authenticate anyone, so {@code /apply} stays
 * closed rather than silently re-opening).
 */
@Slf4j
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Service-Token";
    public static final String INTERNAL_SERVICE_AUTHORITY = "ROLE_INTERNAL_SERVICE";
    private static final String PRINCIPAL = "internal-service";

    private final byte[] expectedToken;

    public InternalServiceTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken == null || expectedToken.isBlank()
                ? null
                : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER_NAME);
        if (matches(presented)) {
            AbstractAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated internal service caller for {} {}",
                    request.getMethod(), request.getRequestURI());
        } else if (presented != null) {
            log.warn("Rejected {} header on {} {} — value does not match the configured service token",
                    HEADER_NAME, request.getMethod(), request.getRequestURI());
        }
        chain.doFilter(request, response);
    }

    private boolean matches(String presented) {
        if (expectedToken == null || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8));
    }
}
