package com.ecommerce.common.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

/**
 * Helper class for setting up security context in tests.
 * Provides convenient methods to mock authenticated users.
 */
public class SecurityContextTestHelper {

    private SecurityContextTestHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Sets up a security context with a basic authenticated user.
     *
     * @param userId user ID (subject)
     */
    public static void setupSecurityContext(String userId) {
        Jwt jwt = JwtTestHelper.createJwt(userId);
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with a user having specific scopes.
     *
     * @param userId user ID (subject)
     * @param scopes list of scopes
     */
    public static void setupSecurityContextWithScopes(String userId, String... scopes) {
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationTokenWithScopes(userId, scopes);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with a user having specific permissions.
     *
     * @param userId user ID (subject)
     * @param permissions list of permissions
     */
    public static void setupSecurityContextWithPermissions(String userId, List<String> permissions) {
        Jwt jwt = JwtTestHelper.createJwtWithPermissions(userId, permissions);
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with an admin user.
     */
    public static void setupAdminSecurityContext() {
        Jwt jwt = JwtTestHelper.createAdminJwt();
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with a user having email and profile info.
     *
     * @param userId user ID (subject)
     * @param email user email
     * @param name full name
     */
    public static void setupSecurityContextWithProfile(String userId, String email, String name) {
        Jwt jwt = JwtTestHelper.createJwtWithProfile(userId, email, name, name.split(" ")[0]);
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with a custom JWT.
     *
     * @param jwt JWT token
     */
    public static void setupSecurityContext(Jwt jwt) {
        JwtAuthenticationToken authentication = JwtTestHelper.createAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Sets up a security context with a custom authentication.
     *
     * @param authentication authentication object
     */
    public static void setupSecurityContext(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Clears the security context.
     * Should be called in @AfterEach or similar cleanup methods.
     */
    public static void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Gets the current security context.
     *
     * @return security context
     */
    public static SecurityContext getSecurityContext() {
        return SecurityContextHolder.getContext();
    }

    /**
     * Checks if there's an authenticated user in the current context.
     *
     * @return true if authenticated
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
