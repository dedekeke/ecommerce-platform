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
 * <h2>What is an orchestration saga?</h2>
 * <p>An orchestration saga is a long-running business transaction split into
 * many local transactions, each in its own service. A single coordinator
 * (this class) tells each service "do step N" in sequence. If a step fails,
 * the orchestrator runs <em>compensating</em> actions — reverse-order rollbacks
 * — for the steps that already succeeded. There is no native distributed
 * transaction; consistency is eventual and developer-driven.</p>
 *
 * <h2>Pros</h2>
 * <ul>
 *   <li>Single place to look when debugging — the flow lives here.</li>
 *   <li>Easy to add / reorder / branch steps without changing services.</li>
 *   <li>Centralised retry, timeout and compensation policy.</li>
 *   <li>Saga state ({@link RefundSagaState}) gives you a clean audit log.</li>
 * </ul>
 *
 * <h2>Cons</h2>
 * <ul>
 *   <li>Orchestrator can become a god-object as more sagas are added.</li>
 *   <li>Tight coupling: orchestrator knows every participant's API.</li>
 *   <li>If the orchestrator JVM crashes mid-flow, the saga is stuck —
 *       mitigated here by persisting {@link RefundSagaState} per step and
 *       running {@link RefundSagaRecoveryScheduler} every 5 minutes.</li>
 * </ul>
 *
 * <h2>Compare with choreography</h2>
 * <p>In a <em>choreography</em> saga (see the upcoming
 * {@code services/inventory-service/.../saga/replenishment/InventoryReplenishmentChoreography}
 * package) there is no central coordinator: each service reacts to events
 * published by the previous one. Choreography decouples services but the
 * flow is implicit — no single class describes "what happens after step 2".
 * Rule of thumb: orchestration when one team owns the workflow, choreography
 * when many teams own pieces and you want loose coupling.</p>
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
 *   <li>VALIDATE fails -> nothing to compensate, mark FAILED.</li>
 *   <li>REVERSE_PAYMENT fails -> nothing completed, mark FAILED.</li>
 *   <li>RESTORE_INVENTORY fails -> compensate REVERSE_PAYMENT (re-charge).</li>
 *   <li>UPDATE_ORDER fails -> compensate inventory then payment.</li>
 *   <li>NOTIFY is best-effort and never fails the saga.</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <p>Every step's {@code execute} MUST be safely re-runnable so the recovery
 * scheduler can resume a saga from the last persisted point without
 * double-charging or double-restoring stock. We achieve this with the
 * {@code restoration_id}/{@code refund_id} keys passed to participants.</p>
 *
 * <p>Pedagogical note: this saga is intentionally <em>hand-rolled</em>; we
 * avoid Axon/Eventuate so the mechanism is visible. In production you might
 * adopt a framework once the patterns repeat.</p>
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
     */
    private void compensate(RefundSagaContext ctx, RefundSagaStep failedStep) {
        RefundSagaState state = ctx.getState();
        state.setStatus(RefundSagaStatus.COMPENSATING);
        sagaRepository.save(state);
        log.warn("Compensating refund saga {} after failure at {}", state.getId(), failedStep);

        List<RefundSagaStep> completed = state.completedStepsInOrder();
        for (int i = completed.size() - 1; i >= 0; i--) {
            RefundSagaStep stepId = completed.get(i);
            try {
                stepsById.get(stepId).compensate(ctx);
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
