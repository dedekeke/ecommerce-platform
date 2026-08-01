package com.ecommerce.orderservice.subscription;

/**
 * Lifecycle states for a recurring subscription (§3.6).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — eligible for the scheduler to fire when due.</li>
 *   <li>{@link #PAUSED} — temporarily skipped; can be resumed.</li>
 *   <li>{@link #CANCELLED} — terminal; rows are kept for history but never run.</li>
 * </ul>
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED
}
