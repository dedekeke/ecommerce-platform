package com.ecommerce.orderservice.security;

import com.ecommerce.orderservice.exception.UserMismatchException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Single source of truth for turning an (untrusted) client-supplied userId into
 * the effective owning user across every order-service controller.
 *
 * <p>The authenticated JWT {@code sub} is authoritative — a userId taken from a
 * request body, header or path is only a correlation hint. If it disagrees with
 * the token subject the request is rejected with {@link UserMismatchException}
 * (HTTP 403).</p>
 *
 * <p><b>Admin semantics:</b> a token carrying the {@code SCOPE_admin} authority
 * may act on behalf of any user, so an explicit userId it supplies is honoured
 * rather than rejected — mirroring the {@code SCOPE_admin} bypass already used by
 * {@code RmaController.getUserReturns}.</p>
 *
 * <p><b>Local-dev fallback:</b> when no JWT principal is present (security
 * disabled for local development) the supplied value is used as-is, matching
 * payment-service.</p>
 */
@Component
public class UserIdentityResolver {

    private static final String ADMIN_AUTHORITY = "SCOPE_admin";

    /**
     * Resolve the effective owning user for an operation keyed on a
     * client-supplied userId (order/subscription creation, per-user listings).
     */
    public String resolveUserId(String clientUserId, Jwt jwt) {
        if (jwt == null) {
            return clientUserId;
        }
        String subject = jwt.getSubject();
        if (isAdmin()) {
            return StringUtils.hasText(clientUserId) ? clientUserId : subject;
        }
        if (StringUtils.hasText(clientUserId) && !clientUserId.equals(subject)) {
            throw new UserMismatchException("Request userId does not match the authenticated user");
        }
        return subject;
    }

    /**
     * IDOR guard for endpoints that look a resource up by an opaque id and only
     * afterwards learn its owner: the caller must own the resource or hold admin
     * scope. No-op in local dev (no JWT principal present).
     */
    public void assertCanActFor(String resourceOwnerUserId, Jwt jwt) {
        if (!canAccess(resourceOwnerUserId, jwt)) {
            throw new UserMismatchException("You are not allowed to access this resource");
        }
    }

    /**
     * Non-throwing ownership check for opaque-id lookup endpoints that must NOT
     * leak whether a resource exists. The caller returns an identical 404 for
     * both "resource is missing" and "resource exists but isn't yours", so a
     * non-admin cannot enumerate valid ids by distinguishing 403 from 404.
     *
     * @return {@code true} if the caller may see the resource (owner, admin, or
     *     local-dev with no JWT); {@code false} for a non-admin non-owner.
     */
    public boolean canAccess(String resourceOwnerUserId, Jwt jwt) {
        if (jwt == null || isAdmin()) {
            return true;
        }
        return Objects.equals(resourceOwnerUserId, jwt.getSubject());
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }
}
