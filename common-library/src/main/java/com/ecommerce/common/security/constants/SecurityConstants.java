package com.ecommerce.common.security.constants;

/**
 * Common security constants used across all microservices.
 */
public final class SecurityConstants {

    private SecurityConstants() {
        // Private constructor to prevent instantiation
    }

    // Common scopes
    public static final String SCOPE_READ = "read:all";
    public static final String SCOPE_WRITE = "write:all";
    public static final String SCOPE_DELETE = "delete:all";
    public static final String SCOPE_ADMIN = "admin";

    // Product-related permissions
    public static final String PERMISSION_READ_PRODUCTS = "read:products";
    public static final String PERMISSION_WRITE_PRODUCTS = "write:products";
    public static final String PERMISSION_DELETE_PRODUCTS = "delete:products";

    // Order-related permissions
    public static final String PERMISSION_READ_ORDERS = "read:orders";
    public static final String PERMISSION_WRITE_ORDERS = "write:orders";
    public static final String PERMISSION_CANCEL_ORDERS = "cancel:orders";

    // User-related permissions
    public static final String PERMISSION_READ_USERS = "read:users";
    public static final String PERMISSION_WRITE_USERS = "write:users";
    public static final String PERMISSION_DELETE_USERS = "delete:users";

    // Admin permissions
    public static final String PERMISSION_ADMIN_ACCESS = "admin:access";
    public static final String PERMISSION_ADMIN_FULL = "admin:full";

    // Roles
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_MANAGER = "ROLE_MANAGER";

    // Authority prefixes (used by Spring Security)
    public static final String SCOPE_PREFIX = "SCOPE_";
    public static final String ROLE_PREFIX = "ROLE_";

    // JWT claim names
    public static final String CLAIM_SUB = "sub";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_SCOPE = "scope";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_ROLES = "roles";

    // Public endpoints (no authentication required)
    public static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/info",
            "/error",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };
}
