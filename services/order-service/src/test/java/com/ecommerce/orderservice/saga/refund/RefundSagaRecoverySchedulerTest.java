package com.ecommerce.orderservice.saga.refund;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundSagaRecoverySchedulerTest {

    @Mock private RefundSagaRepository sagaRepository;
    @Mock private RefundOrchestrator orchestrator;

    private Clock clock;
    private RefundSagaRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
        scheduler = new RefundSagaRecoveryScheduler(sagaRepository, orchestrator, clock);
    }

    @Test
    void should_resumeStuckSagas_olderThanFiveMinutes() {
        RefundSagaState a = RefundSagaState.builder().id("a")
            .status(RefundSagaStatus.IN_PROGRESS).build();
        RefundSagaState b = RefundSagaState.builder().id("b")
            .status(RefundSagaStatus.COMPENSATING).build();
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of(a, b));

        scheduler.recoverStuckSagas();

        verify(orchestrator, times(1)).resume(a);
        verify(orchestrator, times(1)).resume(b);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(sagaRepository).findByStatusInAndUpdatedAtBefore(anyList(), cutoff.capture());
        assertThat(cutoff.getValue())
            .isEqualTo(LocalDateTime.now(clock).minusMinutes(5));
    }

    @Test
    void should_doNothing_whenNoStuckSagas() {
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of());

        scheduler.recoverStuckSagas();

        verify(orchestrator, never()).resume(any());
    }

    @Test
    void should_continue_whenIndividualResumeThrows() {
        RefundSagaState a = RefundSagaState.builder().id("a")
            .status(RefundSagaStatus.IN_PROGRESS).build();
        RefundSagaState b = RefundSagaState.builder().id("b")
            .status(RefundSagaStatus.IN_PROGRESS).build();
        when(sagaRepository.findByStatusInAndUpdatedAtBefore(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of(a, b));
        when(orchestrator.resume(a)).thenThrow(new RuntimeException("first failed"));

        scheduler.recoverStuckSagas();

        verify(orchestrator).resume(b);
    }
}
