package com.ecommerce.orderservice.saga.refund;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefundSagaRunner} — the dedicated async dispatcher that
 * exists so {@code @Async} is honoured (a self-invocation on the orchestrator
 * would bypass the Spring proxy and run synchronously).
 */
@ExtendWith(MockitoExtension.class)
class RefundSagaRunnerTest {

    @Mock private RefundOrchestrator orchestrator;
    @InjectMocks private RefundSagaRunner runner;

    @Test
    void runAsync_should_delegateToOrchestratorRun() throws ExecutionException, InterruptedException {
        RefundSagaContext ctx = RefundSagaContext.builder().orderId("o1").build();
        RefundSagaState state = RefundSagaState.builder().id("s1").build();
        when(orchestrator.run(ctx)).thenReturn(state);

        CompletableFuture<RefundSagaState> future = runner.runAsync(ctx);

        assertThat(future.get()).isSameAs(state);
        verify(orchestrator, times(1)).run(ctx);
    }
}
