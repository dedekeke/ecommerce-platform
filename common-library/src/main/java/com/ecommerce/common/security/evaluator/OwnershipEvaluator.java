package com.ecommerce.common.security.evaluator;

import com.ecommerce.common.security.util.JwtClaimsExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Evaluator for checking resource ownership.
 * Services can extend this class and override methods to implement custom ownership logic.
 */
@Component("ownershipEvaluator")
public class OwnershipEvaluator {

    /**
     * Checks if the authenticated user is the owner of a resource or is an admin.
     * This is a basic implementation that services can override.
     *
     * @param authentication the authentication object
     * @param resourceOwnerId the user ID that owns the resource
     * @return true if user is owner or admin
     */
    public boolean isOwnerOrAdmin(Authentication authentication, String resourceOwnerId) {
        if (authentication == null || resourceOwnerId == null) {
            return false;
        }

        // Check if user is admin
        if (isAdmin(authentication)) {
            return true;
        }

        // Check if user is the owner
        String currentUserId = getCurrentUserId(authentication);
        return resourceOwnerId.equals(currentUserId);
    }

    /**
     * Checks if user is admin.
     *
     * @param authentication the authentication object
     * @return true if user has admin authority
     */
    protected boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN") ||
                        authority.getAuthority().equals("SCOPE_admin") ||
                        authority.getAuthority().equals("admin:access")
                );
    }

    /**
     * Extracts the current user ID from authentication.
     *
     * @param authentication the authentication object
     * @return user ID or null
     */
    protected String getCurrentUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return JwtClaimsExtractor.getUserId(jwt);
        }
        return null;
    }

    /**
     * Checks if user owns a resource identified by numeric ID.
     * Services should override this with their own logic to fetch and check ownership.
     *
     * @param authentication the authentication object
     * @param resourceId the resource ID
     * @return true if user owns the resource or is admin
     */
    public boolean isOwnerOrAdmin(Authentication authentication, Long resourceId) {
        // Default implementation - services should override this
        // This is a placeholder for numeric ID-based resources
        return isAdmin(authentication);
    }
}
