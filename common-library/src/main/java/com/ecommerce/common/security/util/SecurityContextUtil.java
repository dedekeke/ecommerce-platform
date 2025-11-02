package com.ecommerce.common.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Utility class for working with Spring Security Context.
 * Provides convenient access to authentication information.
 */
public class SecurityContextUtil {

    private SecurityContextUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets the current authentication object.
     *
     * @return Optional containing authentication if present
     */
    public static Optional<Authentication> getCurrentAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Checks if the current user is authenticated.
     *
     * @return true if user is authenticated
     */
    public static boolean isAuthenticated() {
        return getCurrentAuthentication()
                .map(Authentication::isAuthenticated)
                .orElse(false);
    }

    /**
     * Gets the current user's principal.
     *
     * @return principal object or null if not authenticated
     */
    public static Object getPrincipal() {
        return getCurrentAuthentication()
                .map(Authentication::getPrincipal)
                .orElse(null);
    }

    /**
     * Gets the current user's authorities (roles, scopes, permissions).
     *
     * @return collection of granted authorities
     */
    public static Collection<? extends GrantedAuthority> getAuthorities() {
        return getCurrentAuthentication()
                .map(Authentication::getAuthorities)
                .orElse(Collections.emptyList());
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority authority to check
     * @return true if user has the authority
     */
    public static boolean hasAuthority(String authority) {
        return getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    /**
     * Checks if the current user has any of the specified authorities.
     *
     * @param authorities authorities to check
     * @return true if user has any of the authorities
     */
    public static boolean hasAnyAuthority(String... authorities) {
        Collection<? extends GrantedAuthority> userAuthorities = getAuthorities();
        for (String authority : authorities) {
            if (userAuthorities.stream().anyMatch(ga -> ga.getAuthority().equals(authority))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the current user has all of the specified authorities.
     *
     * @param authorities authorities to check
     * @return true if user has all authorities
     */
    public static boolean hasAllAuthorities(String... authorities) {
        Collection<? extends GrantedAuthority> userAuthorities = getAuthorities();
        for (String authority : authorities) {
            if (userAuthorities.stream().noneMatch(ga -> ga.getAuthority().equals(authority))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the JWT token from the current authentication.
     *
     * @return Optional containing JWT if present
     */
    public static Optional<Jwt> getCurrentJwt() {
        return getCurrentAuthentication()
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .map(auth -> ((JwtAuthenticationToken) auth).getToken());
    }

    /**
     * Gets the current user ID from JWT.
     * Convenience method that combines getCurrentJwt and JwtClaimsExtractor.
     *
     * @return user ID or null if not available
     */
    public static String getCurrentUserId() {
        return getCurrentJwt()
                .map(JwtClaimsExtractor::getUserId)
                .orElse(null);
    }

    /**
     * Gets the current user's email from JWT.
     *
     * @return email or null if not available
     */
    public static String getCurrentUserEmail() {
        return getCurrentJwt()
                .map(JwtClaimsExtractor::getEmail)
                .orElse(null);
    }

    /**
     * Clears the security context.
     * Useful for cleanup in tests or after processing.
     */
    public static void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Checks if current user is an admin.
     * Checks for admin role or admin permissions.
     *
     * @return true if user is admin
     */
    public static boolean isAdmin() {
        return hasAnyAuthority("ROLE_ADMIN", "SCOPE_admin", "admin:access");
    }

    /**
     * Checks if the current user has a specific scope.
     * Scopes are prefixed with "SCOPE_" by Spring Security.
     *
     * @param scope scope to check (without SCOPE_ prefix)
     * @return true if user has the scope
     */
    public static boolean hasScope(String scope) {
        return hasAuthority("SCOPE_" + scope);
    }

    /**
     * Checks if the current user has any of the specified scopes.
     *
     * @param scopes scopes to check (without SCOPE_ prefix)
     * @return true if user has any scope
     */
    public static boolean hasAnyScope(String... scopes) {
        for (String scope : scopes) {
            if (hasAuthority("SCOPE_" + scope)) {
                return true;
            }
        }
        return false;
    }
}
