package com.ecommerce.orderservice.saga.refund;

/**
 * Identifies a step inside the refund orchestration saga.
 * Order matters — RefundOrchestrator iterates {@link #values()} top-to-bottom
 * and compensates bottom-to-top.
 */
public enum RefundSagaStep {
    VALIDATE,
    REVERSE_PAYMENT,
    RESTORE_INVENTORY,
    UPDATE_ORDER,
    NOTIFY
}
