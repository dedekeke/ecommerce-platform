package com.ecommerce.orderservice.saga.refund;

import com.ecommerce.orderservice.saga.refund.step.NotifyRefundStep;
import com.ecommerce.orderservice.saga.refund.step.RestoreInventoryStep;
import com.ecommerce.orderservice.saga.refund.step.ReversePaymentStep;
import com.ecommerce.orderservice.saga.refund.step.UpdateOrderStep;
import com.ecommerce.orderservice.saga.refund.step.ValidateRefundStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestration saga that coordinates a customer refund.
 *
 * <p>Sequences five participants — order validation, payment reversal,
 * inventory restoration, order status update, and customer notification —
 * via local transactions. {@link RefundSagaState} is persisted per step so
 * {@link RefundSagaRecoveryScheduler} can resume a saga whose orchestrator
 * crashed mid-flow. Every step's {@code execute} must be idempotent
 * (carried out via {@code restoration_id} / {@code refund_id} keys passed
 * to participants).
 *
 * <h2>Happy-path flow</h2>
 * <pre>
 *  Client                 Orchestrator        Payment   Inventory   Order   Kafka
 *    |  POST /refund         |                  |          |          |       |
 *    |---------------------->|                  |          |          |       |
 *    |   202 + sagaId        | VALIDATE order   |          |          |       |
 *    |&lt;----------------------|----------------------------------|---->|       |
 *    |                       | REVERSE_PAYMENT  |          |          |       |
 *    |                       |----------------->|          |          |       |
 *    |                       | RESTORE_INVENTORY|          |          |       |
 *    |                       |---------------------------->|          |       |
 *    |                       | UPDATE_ORDER     |          |          |       |
 *    |                       |--------------------------------------->|       |
 *    |                       | NOTIFY (best-effort)                           |
 *    |                       |--------------------------------------------->  |
 * </pre>
 *
 * <h2>Failure handling</h2>
 * <ul>
 *   <li>VALIDATE / REVERSE_PAYMENT fail -> nothing to compensate, mark FAILED.</li>
 *   <li>RESTORE_INVENTORY fails -> compensate REVERSE_PAYMENT (re-charge).</li>
 *   <li>UPDATE_ORDER fails -> compensate inventory then payment.</li>
 *   <li>NOTIFY is best-effort and never fails the saga.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefundOrchestrator {

    private final RefundSagaRepository sagaRepository;
    private final Map<RefundSagaStep, SagaStep> stepsById;
    private final List<RefundSagaStep> stepOrder;

    public RefundOrchestrator(
        RefundSagaRepository sagaRepository,
        ValidateRefundStep validateStep,
        ReversePaymentStep reversePaymentStep,
        RestoreInventoryStep restoreInventoryStep,
        UpdateOrderStep updateOrderStep,
        NotifyRefundStep notifyRefundStep
    ) {
        this.sagaRepository = sagaRepository;
        this.stepsById = new EnumMap<>(RefundSagaStep.class);
        this.stepsById.put(RefundSagaStep.VALIDATE, validateStep);
        this.stepsById.put(RefundSagaStep.REVERSE_PAYMENT, reversePaymentStep);
        this.stepsById.put(RefundSagaStep.RESTORE_INVENTORY, restoreInventoryStep);
        this.stepsById.put(RefundSagaStep.UPDATE_ORDER, updateOrderStep);
        this.stepsById.put(RefundSagaStep.NOTIFY, notifyRefundStep);
        this.stepOrder = List.of(RefundSagaStep.values());
    }

    /**
     * Public entry — persists a fresh saga synchronously so the caller has a
     * sagaId to poll, then dispatches the actual run on a background thread.
     */
    public RefundSagaState startRefund(String orderId, String reason, String userEmail) {
        RefundSagaState state = newSagaState(orderId, reason);
        RefundSagaContext ctx = RefundSagaContext.builder()
            .state(state)
            .orderId(orderId)
            .reason(reason)
            .userEmail(userEmail)
            .build();
        runAsync(ctx);
        return state;
    }

    @Async
    @Transactional(propagation = Propagation.NEVER)
    public CompletableFuture<RefundSagaState> runAsync(RefundSagaContext ctx) {
        return CompletableFuture.completedFuture(run(ctx));
    }

    /**
     * Synchronous variant — used by the recovery scheduler and by tests.
     */
    public RefundSagaState run(RefundSagaContext ctx) {
        RefundSagaState state = ctx.getState();
        if (state == null) {
            throw new IllegalStateException("Saga state must be initialised before run()");
        }

        log.info("Starting refund saga {} for order {}", state.getId(), state.getOrderId());
        state.setStatus(RefundSagaStatus.IN_PROGRESS);
        sagaRepository.save(state);

        for (RefundSagaStep stepId : stepOrder) {
            if (state.hasCompleted(stepId)) {
                log.debug("Saga {} skipping already-completed step {}", state.getId(), stepId);
                continue;
            }
            state.setCurrentStep(stepId);
            sagaRepository.save(state);

            SagaStep step = stepsById.get(stepId);
            StepResult result = safelyExecute(step, ctx);
            if (!result.successful()) {
                state.setFailureReason(result.message());
                sagaRepository.save(state);
                compensate(ctx, stepId);
                return finalizeFailed(state);
            }
            state.markStepCompleted(stepId);
            sagaRepository.save(state);
        }

        state.setStatus(RefundSagaStatus.COMPLETED);
        sagaRepository.save(state);
        log.info("Refund saga {} COMPLETED for order {}", state.getId(), state.getOrderId());
        return state;
    }

    /**
     * Resume a saga that was previously persisted but never finished. Used
     * by {@link RefundSagaRecoveryScheduler}.
     */
    public RefundSagaState resume(RefundSagaState state) {
        log.info("Resuming refund saga {} from step {}", state.getId(), state.getCurrentStep());
        RefundSagaContext ctx = RefundSagaContext.builder()
            .state(state)
            .orderId(state.getOrderId())
            .reason(state.getReason())
            .restorationId(state.getRestorationId())
            .refundTransactionId(state.getRefundTransactionId())
            .build();
        return run(ctx);
    }

    private StepResult safelyExecute(SagaStep step, RefundSagaContext ctx) {
        try {
            return step.execute(ctx);
        } catch (Exception e) {
            log.error("Unhandled error during step {}", step.id(), e);
            return StepResult.failure("Unhandled error: " + e.getMessage());
        }
    }

    /**
     * Walk completed steps in reverse and compensate. Failures during
     * compensation are logged but never re-thrown — at this point we have
     * already accepted the saga is doomed; loud crashing helps no one.
     *
     * <p>If no steps were completed (e.g. validation failed first), there is
     * nothing to roll back — the saga is simply marked FAILED, skipping the
     * COMPENSATING / COMPENSATED states.</p>
     */
    private void compensate(RefundSagaContext ctx, RefundSagaStep failedStep) {
        RefundSagaState state = ctx.getState();
        List<RefundSagaStep> completed = state.completedStepsInOrder();
        boolean anyCompensable = completed.stream()
            .map(stepsById::get).anyMatch(SagaStep::hasCompensation);
        if (!anyCompensable) {
            log.warn("Refund saga {} failed at {} with nothing to compensate",
                state.getId(), failedStep);
            return;
        }
        state.setStatus(RefundSagaStatus.COMPENSATING);
        sagaRepository.save(state);
        log.warn("Compensating refund saga {} after failure at {}", state.getId(), failedStep);

        for (int i = completed.size() - 1; i >= 0; i--) {
            RefundSagaStep stepId = completed.get(i);
            SagaStep stepImpl = stepsById.get(stepId);
            if (!stepImpl.hasCompensation()) {
                continue;
            }
            try {
                stepImpl.compensate(ctx);
            } catch (Exception e) {
                log.error("Compensation for step {} threw — continuing", stepId, e);
            }
        }
        state.setStatus(RefundSagaStatus.COMPENSATED);
        sagaRepository.save(state);
    }

    private RefundSagaState finalizeFailed(RefundSagaState state) {
        if (state.getStatus() != RefundSagaStatus.COMPENSATED) {
            state.setStatus(RefundSagaStatus.FAILED);
            sagaRepository.save(state);
        }
        log.warn("Refund saga {} ended as {} (reason: {})",
            state.getId(), state.getStatus(), state.getFailureReason());
        return state;
    }

    private RefundSagaState newSagaState(String orderId, String reason) {
        RefundSagaState state = RefundSagaState.builder()
            .orderId(orderId)
            .reason(reason)
            .status(RefundSagaStatus.PENDING)
            .currentStep(RefundSagaStep.VALIDATE)
            .build();
        return sagaRepository.save(state);
    }

    public Optional<RefundSagaState> findSaga(String sagaId) {
        return sagaRepository.findById(sagaId);
    }
}
