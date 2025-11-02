package com.ecommerce.common.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for extracting claims from JWT tokens.
 * Provides convenient methods to access common and custom claims.
 */
public class JwtClaimsExtractor {

    private JwtClaimsExtractor() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets the JWT from the current security context.
     *
     * @return Optional containing JWT if present
     */
    public static Optional<Jwt> getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }

        return Optional.empty();
    }

    /**
     * Extracts the user ID (subject) from the current JWT.
     *
     * @return user ID or null if not authenticated
     */
    public static String getCurrentUserId() {
        return getCurrentJwt()
                .map(Jwt::getSubject)
                .orElse(null);
    }

    /**
     * Extracts the user ID (subject) from a JWT.
     *
     * @param jwt JWT token
     * @return user ID
     */
    public static String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Extracts email from JWT.
     * Auth0 typically includes email in the token.
     *
     * @param jwt JWT token
     * @return email or null if not present
     */
    public static String getEmail(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }

    /**
     * Extracts email from the current JWT.
     *
     * @return email or null if not present
     */
    public static String getCurrentEmail() {
        return getCurrentJwt()
                .map(JwtClaimsExtractor::getEmail)
                .orElse(null);
    }

    /**
     * Extracts name from JWT.
     *
     * @param jwt JWT token
     * @return name or null if not present
     */
    public static String getName(Jwt jwt) {
        return jwt.getClaimAsString("name");
    }

    /**
     * Extracts nickname from JWT.
     *
     * @param jwt JWT token
     * @return nickname or null if not present
     */
    public static String getNickname(Jwt jwt) {
        return jwt.getClaimAsString("nickname");
    }

    /**
     * Extracts picture URL from JWT.
     *
     * @param jwt JWT token
     * @return picture URL or null if not present
     */
    public static String getPicture(Jwt jwt) {
        return jwt.getClaimAsString("picture");
    }

    /**
     * Extracts scopes from JWT.
     * Scopes are typically space-delimited in the "scope" claim.
     *
     * @param jwt JWT token
     * @return list of scopes
     */
    public static List<String> getScopes(Jwt jwt) {
        String scopeClaim = jwt.getClaimAsString("scope");
        if (scopeClaim == null || scopeClaim.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(scopeClaim.split(" "));
    }

    /**
     * Extracts permissions from JWT.
     * Auth0 typically includes permissions as an array.
     *
     * @param jwt JWT token
     * @return list of permissions
     */
    public static List<String> getPermissions(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        return permissions != null ? permissions : Collections.emptyList();
    }

    /**
     * Checks if JWT contains a specific scope.
     *
     * @param jwt JWT token
     * @param scope scope to check
     * @return true if scope is present
     */
    public static boolean hasScope(Jwt jwt, String scope) {
        return getScopes(jwt).contains(scope);
    }

    /**
     * Checks if JWT contains a specific permission.
     *
     * @param jwt JWT token
     * @param permission permission to check
     * @return true if permission is present
     */
    public static boolean hasPermission(Jwt jwt, String permission) {
        return getPermissions(jwt).contains(permission);
    }

    /**
     * Checks if current user has a specific scope.
     *
     * @param scope scope to check
     * @return true if scope is present
     */
    public static boolean currentUserHasScope(String scope) {
        return getCurrentJwt()
                .map(jwt -> hasScope(jwt, scope))
                .orElse(false);
    }

    /**
     * Checks if current user has a specific permission.
     *
     * @param permission permission to check
     * @return true if permission is present
     */
    public static boolean currentUserHasPermission(String permission) {
        return getCurrentJwt()
                .map(jwt -> hasPermission(jwt, permission))
                .orElse(false);
    }

    /**
     * Extracts a custom claim as String.
     *
     * @param jwt JWT token
     * @param claimName name of the claim
     * @return claim value or null if not present
     */
    public static String getClaimAsString(Jwt jwt, String claimName) {
        return jwt.getClaimAsString(claimName);
    }

    /**
     * Extracts a custom claim as List.
     *
     * @param jwt JWT token
     * @param claimName name of the claim
     * @return claim value as list or empty list if not present
     */
    public static List<String> getClaimAsStringList(Jwt jwt, String claimName) {
        List<String> claim = jwt.getClaimAsStringList(claimName);
        return claim != null ? claim : Collections.emptyList();
    }

    /**
     * Extracts a custom claim as Map.
     *
     * @param jwt JWT token
     * @param claimName name of the claim
     * @return claim value as map or empty map if not present
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getClaimAsMap(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (claim instanceof Map) {
            return (Map<String, Object>) claim;
        }
        return Collections.emptyMap();
    }

    /**
     * Gets token expiration time.
     *
     * @param jwt JWT token
     * @return expiration instant
     */
    public static Instant getExpiresAt(Jwt jwt) {
        return jwt.getExpiresAt();
    }

    /**
     * Gets token issued at time.
     *
     * @param jwt JWT token
     * @return issued at instant
     */
    public static Instant getIssuedAt(Jwt jwt) {
        return jwt.getIssuedAt();
    }

    /**
     * Checks if token is expired.
     *
     * @param jwt JWT token
     * @return true if token is expired
     */
    public static boolean isExpired(Jwt jwt) {
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets all claims from JWT.
     *
     * @param jwt JWT token
     * @return map of all claims
     */
    public static Map<String, Object> getAllClaims(Jwt jwt) {
        return jwt.getClaims();
    }

    /**
     * Extracts custom app metadata from JWT.
     * Auth0 allows storing custom metadata in tokens.
     *
     * @param jwt JWT token
     * @param namespace custom namespace (e.g., "https://your-app.com/")
     * @return metadata map
     */
    public static Map<String, Object> getAppMetadata(Jwt jwt, String namespace) {
        return getClaimAsMap(jwt, namespace + "app_metadata");
    }

    /**
     * Extracts custom user metadata from JWT.
     *
     * @param jwt JWT token
     * @param namespace custom namespace
     * @return metadata map
     */
    public static Map<String, Object> getUserMetadata(Jwt jwt, String namespace) {
        return getClaimAsMap(jwt, namespace + "user_metadata");
    }

    /**
     * Extracts roles from custom namespace.
     * Common pattern in Auth0 to include roles in a custom claim.
     *
     * @param jwt JWT token
     * @param namespace custom namespace
     * @return list of roles
     */
    public static List<String> getRoles(Jwt jwt, String namespace) {
        return getClaimAsStringList(jwt, namespace + "roles");
    }
}
