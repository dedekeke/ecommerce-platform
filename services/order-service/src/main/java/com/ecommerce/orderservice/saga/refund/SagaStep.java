package com.ecommerce.orderservice.saga.refund;

/**
 * Contract every refund-saga step must implement. Two concerns:
 * <ul>
 *   <li>{@link #execute(RefundSagaContext)} — forward action.
 *       MUST be idempotent so the recovery scheduler can safely re-run.</li>
 *   <li>{@link #compensate(RefundSagaContext)} — best-effort rollback.
 *       Called by the orchestrator when a later step fails. Steps that
 *       have nothing meaningful to undo (e.g. validation, notification)
 *       leave it as a no-op default.</li>
 * </ul>
 */
public interface SagaStep {

    RefundSagaStep id();

    StepResult execute(RefundSagaContext ctx);

    default void compensate(RefundSagaContext ctx) {
        // no-op by default
    }

    /**
     * Steps that own no rollback (validation, best-effort notifications) return
     * {@code false} and the orchestrator skips invoking their {@link #compensate}
     * entirely. Steps with real undo work (payment, inventory, order status)
     * override this to {@code true}.
     */
    default boolean hasCompensation() {
        return false;
    }
}
