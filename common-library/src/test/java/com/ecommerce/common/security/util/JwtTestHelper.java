package com.ecommerce.common.security.util;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for creating mock JWT tokens in tests.
 * Provides convenient methods to create JWTs with various claims and authorities.
 */
public class JwtTestHelper {

    private static final String DEFAULT_ISSUER = "https://test.auth0.com/";
    private static final String DEFAULT_SUBJECT = "auth0|test-user-123";
    private static final String DEFAULT_AUDIENCE = "https://api.ecommerce.com";

    private JwtTestHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a basic JWT with default claims.
     *
     * @return JWT token
     */
    public static Jwt createJwt() {
        return createJwt(DEFAULT_SUBJECT, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Creates a JWT with specific subject.
     *
     * @param subject user ID (sub claim)
     * @return JWT token
     */
    public static Jwt createJwt(String subject) {
        return createJwt(subject, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Creates a JWT with specific subject and scopes.
     *
     * @param subject user ID (sub claim)
     * @param scopes list of scopes
     * @return JWT token
     */
    public static Jwt createJwtWithScopes(String subject, List<String> scopes) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", String.join(" ", scopes));
        return createJwt(subject, Collections.emptyList(), claims);
    }

    /**
     * Creates a JWT with specific subject and permissions.
     *
     * @param subject user ID (sub claim)
     * @param permissions list of permissions
     * @return JWT token
     */
    public static Jwt createJwtWithPermissions(String subject, List<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("permissions", permissions);
        return createJwt(subject, permissions, claims);
    }

    /**
     * Creates a JWT with custom claims.
     *
     * @param subject user ID (sub claim)
     * @param permissions list of permissions
     * @param additionalClaims additional custom claims
     * @return JWT token
     */
    public static Jwt createJwt(String subject, List<String> permissions, Map<String, Object> additionalClaims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        Map<String, Object> claims = new HashMap<>(additionalClaims);
        claims.put("sub", subject);
        claims.put("iss", DEFAULT_ISSUER);
        claims.put("aud", List.of(DEFAULT_AUDIENCE));
        claims.put("iat", now);
        claims.put("exp", expiresAt);

        if (permissions != null && !permissions.isEmpty()) {
            claims.put("permissions", permissions);
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        return new Jwt(
                "mock-token-" + UUID.randomUUID(),
                now,
                expiresAt,
                headers,
                claims
        );
    }

    /**
     * Creates a JWT with email claim.
     *
     * @param subject user ID
     * @param email user email
     * @return JWT token
     */
    public static Jwt createJwtWithEmail(String subject, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("email_verified", true);
        return createJwt(subject, Collections.emptyList(), claims);
    }

    /**
     * Creates a JWT with user profile claims.
     *
     * @param subject user ID
     * @param email user email
     * @param name full name
     * @param nickname nickname
     * @return JWT token
     */
    public static Jwt createJwtWithProfile(String subject, String email, String name, String nickname) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("email_verified", true);
        claims.put("name", name);
        claims.put("nickname", nickname);
        claims.put("picture", "https://example.com/avatar.jpg");
        return createJwt(subject, Collections.emptyList(), claims);
    }

    /**
     * Creates a JWT for an admin user.
     *
     * @return JWT token with admin scope
     */
    public static Jwt createAdminJwt() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "admin read:all write:all delete:all");
        claims.put("permissions", List.of("admin:access", "admin:full"));
        return createJwt("auth0|admin-user", List.of("admin:access", "admin:full"), claims);
    }

    /**
     * Creates an expired JWT.
     *
     * @param subject user ID
     * @return expired JWT token
     */
    public static Jwt createExpiredJwt(String subject) {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant expired = Instant.now().minus(1, ChronoUnit.HOURS);

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("iss", DEFAULT_ISSUER);
        claims.put("aud", List.of(DEFAULT_AUDIENCE));
        claims.put("iat", past);
        claims.put("exp", expired);

        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        return new Jwt(
                "mock-expired-token-" + UUID.randomUUID(),
                past,
                expired,
                headers,
                claims
        );
    }

    /**
     * Creates a JwtAuthenticationToken for use in tests.
     * This can be used with @WithMockUser or SecurityContext.
     *
     * @param jwt JWT token
     * @return JwtAuthenticationToken
     */
    public static JwtAuthenticationToken createAuthenticationToken(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    /**
     * Creates a JwtAuthenticationToken with specific scopes.
     *
     * @param subject user ID
     * @param scopes list of scopes
     * @return JwtAuthenticationToken
     */
    public static JwtAuthenticationToken createAuthenticationTokenWithScopes(String subject, String... scopes) {
        Jwt jwt = createJwtWithScopes(subject, List.of(scopes));
        return createAuthenticationToken(jwt);
    }

    /**
     * Extracts authorities from JWT for authentication.
     *
     * @param jwt JWT token
     * @return list of authorities
     */
    private static List<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // Extract scopes
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isEmpty()) {
            authorities.addAll(
                    Arrays.stream(scope.split(" "))
                            .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                            .collect(Collectors.toList())
            );
        }

        // Extract permissions
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null && !permissions.isEmpty()) {
            authorities.addAll(
                    permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList())
            );
        }

        return authorities;
    }
}
