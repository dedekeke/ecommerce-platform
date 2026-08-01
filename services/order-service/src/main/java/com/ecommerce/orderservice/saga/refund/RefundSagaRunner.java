package com.ecommerce.orderservice.saga.refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Dedicated async dispatcher for the refund saga.
 *
 * <p>Lives in its own Spring bean on purpose: {@code @Async} is implemented via
 * an AOP proxy, so a self-invocation (e.g. {@code this.runAsync(...)} from
 * inside {@link RefundOrchestrator}) bypasses the proxy and runs synchronously
 * on the caller's thread. Routing the async hop through a separate bean means
 * the call crosses the proxy boundary and actually runs on the
 * {@code refundExecutor} pool.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundSagaRunner {

    private final RefundOrchestrator orchestrator;

    /**
     * Run the saga on the bounded {@code refundExecutor} pool. The persisted
     * {@link RefundSagaState} is the source of truth; if this JVM dies
     * mid-flow the recovery scheduler resumes from the last persisted step.
     */
    @Async("refundExecutor")
    public CompletableFuture<RefundSagaState> runAsync(RefundSagaContext ctx) {
        log.debug("Dispatching refund saga {} on async executor",
            ctx.getState() != null ? ctx.getState().getId() : "<new>");
        return CompletableFuture.completedFuture(orchestrator.run(ctx));
    }
}
