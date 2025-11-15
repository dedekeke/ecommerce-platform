package com.ecommerce.cartservice.domain;

/**
 * Cart Status Enum
 *
 * Represents the current status of a shopping cart.
 */
public enum CartStatus {
    /**
     * Active cart - user is actively adding/removing items
     */
    ACTIVE,

    /**
     * Cart has been checked out and converted to an order
     */
    CHECKED_OUT,

    /**
     * Cart was abandoned (inactive for a long time)
     */
    ABANDONED,

    /**
     * Cart was merged into another cart (e.g., anonymous to authenticated)
     */
    MERGED
}
