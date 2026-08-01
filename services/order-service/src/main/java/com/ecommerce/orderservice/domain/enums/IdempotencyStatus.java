package com.ecommerce.orderservice.domain.enums;

/**
 * Lifecycle of an {@link com.ecommerce.orderservice.domain.entity.IdempotencyKey}.
 *
 * <p>{@code IN_PROGRESS} is written before the order-creation saga runs and acts
 * as the concurrency reservation; {@code COMPLETED} is written with the resulting
 * order id once the saga succeeds. A failed saga deletes the row so a real retry
 * can create the order.</p>
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
