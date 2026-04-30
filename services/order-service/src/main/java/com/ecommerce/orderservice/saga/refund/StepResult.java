package com.ecommerce.orderservice.saga.refund;

/**
 * Outcome of a single saga step. Steps return — they don't throw — so the
 * orchestrator can record the failure reason cleanly into RefundSagaState.
 *
 * <p>Use {@link #ok()} for happy path, {@link #failure(String)} for
 * recoverable / business-rule failures. Step implementations can still throw
 * unchecked exceptions for unexpected bugs; the orchestrator treats those as
 * failures and triggers compensation.</p>
 */
public record StepResult(boolean successful, String message) {

    public static StepResult ok() {
        return new StepResult(true, null);
    }

    public static StepResult failure(String message) {
        return new StepResult(false, message);
    }
}
