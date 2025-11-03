package com.ecommerce.userservice.domain;

/**
 * User Role Enum
 *
 * Defines the roles a user can have in the system.
 * These roles are synced from Auth0 and used for authorization.
 */
public enum UserRole {
    /**
     * Regular customer user
     */
    USER,

    /**
     * Administrator with full access
     */
    ADMIN,

    /**
     * Support staff with limited access
     */
    SUPPORT,

    /**
     * Warehouse/inventory manager
     */
    WAREHOUSE_MANAGER
}
