package com.ecommerce.orderservice.saga.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("RmaRecoveryScheduler")
class RmaRecoverySchedulerTest {

    @Mock private ReturnRepository returnRepository;
    @Mock private RmaOrchestrator orchestrator;

    private Clock clock;
    private RmaRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
        scheduler = new RmaRecoveryScheduler(returnRepository, orchestrator, clock);
    }

    @Test
    @DisplayName("should_resumeStuckReturns_olderThanFiveMinutes")
    void should_resumeStuckReturns_olderThanFiveMinutes() {
        Return a = Return.builder().id("a").rmaNumber("RMA-A").status(ReturnStatus.REQUESTED).build();
        Return b = Return.builder().id("b").rmaNumber("RMA-B").status(ReturnStatus.INSPECTING).build();
        when(returnRepository.findByStatusInAndUpdatedAtBeforeWithLines(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of(a, b));

        scheduler.recoverStuckRmas();

        verify(orchestrator, times(1)).resume(a);
        verify(orchestrator, times(1)).resume(b);
        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(returnRepository).findByStatusInAndUpdatedAtBeforeWithLines(anyList(), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(LocalDateTime.now(clock).minusMinutes(5));
    }

    @Test
    @DisplayName("should_doNothing_when_noStuckReturns")
    void should_doNothing_when_noStuckReturns() {
        when(returnRepository.findByStatusInAndUpdatedAtBeforeWithLines(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of());

        scheduler.recoverStuckRmas();

        verify(orchestrator, never()).resume(any());
    }

    @Test
    @DisplayName("should_continueRecovery_when_individualResumeThrows")
    void should_continueRecovery_when_individualResumeThrows() {
        Return a = Return.builder().id("a").rmaNumber("RMA-A").status(ReturnStatus.REQUESTED).build();
        Return b = Return.builder().id("b").rmaNumber("RMA-B").status(ReturnStatus.NOTIFIED).build();
        when(returnRepository.findByStatusInAndUpdatedAtBeforeWithLines(anyList(), any(LocalDateTime.class)))
            .thenReturn(List.of(a, b));
        when(orchestrator.resume(a)).thenThrow(new RuntimeException("first failed"));

        scheduler.recoverStuckRmas();

        verify(orchestrator).resume(b);
    }

    @Test
    @DisplayName("recoverable_status_list_should_excludeAwaitingShipment")
    void recoverable_status_list_should_excludeAwaitingShipment() {
        // AWAITING_SHIPMENT is a long-poll state — re-driving it would fire
        // duplicate notifications. Make sure it's not in the recovery filter.
        assertThat(RmaRecoveryScheduler.RECOVERABLE_STATUSES)
            .doesNotContain(ReturnStatus.AWAITING_SHIPMENT)
            .doesNotContain(ReturnStatus.COMPLETED)
            .doesNotContain(ReturnStatus.REJECTED)
            .doesNotContain(ReturnStatus.FAILED)
            .contains(ReturnStatus.REQUESTED, ReturnStatus.NOTIFIED, ReturnStatus.INSPECTING);
    }
}
