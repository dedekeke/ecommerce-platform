package com.ecommerce.orderservice.saga.refund;

import com.ecommerce.orderservice.saga.refund.step.NotifyRefundStep;
import com.ecommerce.orderservice.saga.refund.step.RestoreInventoryStep;
import com.ecommerce.orderservice.saga.refund.step.ReversePaymentStep;
import com.ecommerce.orderservice.saga.refund.step.UpdateOrderStep;
import com.ecommerce.orderservice.saga.refund.step.ValidateRefundStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundOrchestratorTest {

    @Mock private RefundSagaRepository sagaRepository;
    @Mock private ValidateRefundStep validateStep;
    @Mock private ReversePaymentStep reversePaymentStep;
    @Mock private RestoreInventoryStep restoreInventoryStep;
    @Mock private UpdateOrderStep updateOrderStep;
    @Mock private NotifyRefundStep notifyRefundStep;

    private RefundOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new RefundOrchestrator(sagaRepository, validateStep, reversePaymentStep,
            restoreInventoryStep, updateOrderStep, notifyRefundStep);
        lenient().when(sagaRepository.save(any(RefundSagaState.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(reversePaymentStep.hasCompensation()).thenReturn(true);
        lenient().when(restoreInventoryStep.hasCompensation()).thenReturn(true);
        lenient().when(updateOrderStep.hasCompensation()).thenReturn(true);
    }

    @Test
    void should_completeAllSteps_onHappyPath() {
        when(validateStep.execute(any())).thenReturn(StepResult.ok());
        when(reversePaymentStep.execute(any())).thenReturn(StepResult.ok());
        when(restoreInventoryStep.execute(any())).thenReturn(StepResult.ok());
        when(updateOrderStep.execute(any())).thenReturn(StepResult.ok());
        when(notifyRefundStep.execute(any())).thenReturn(StepResult.ok());

        RefundSagaState state = orchestrator.startRefund("order-1", "size", "u@x.com");

        assertThat(state.getStatus()).isEqualTo(RefundSagaStatus.COMPLETED);
        assertThat(state.getCompletedSteps()).containsExactlyInAnyOrderElementsOf(
            EnumSet.allOf(RefundSagaStep.class));
        verify(validateStep).execute(any());
        verify(reversePaymentStep).execute(any());
        verify(restoreInventoryStep).execute(any());
        verify(updateOrderStep).execute(any());
        verify(notifyRefundStep).execute(any());
        verify(reversePaymentStep, never()).compensate(any());
    }

    @Test
    void should_markFailed_andSkipCompensation_whenValidateFails() {
        when(validateStep.execute(any())).thenReturn(StepResult.failure("not eligible"));

        RefundSagaState state = orchestrator.startRefund("order-1", "size", null);

        assertThat(state.getStatus()).isEqualTo(RefundSagaStatus.FAILED);
        assertThat(state.getFailureReason()).contains("not eligible");
        verify(reversePaymentStep, never()).execute(any());
        verify(validateStep, never()).compensate(any());
    }

    @Test
    void should_compensateReversePayment_whenRestoreInventoryFails() {
        when(validateStep.execute(any())).thenReturn(StepResult.ok());
        when(reversePaymentStep.execute(any())).thenReturn(StepResult.ok());
        when(restoreInventoryStep.execute(any())).thenReturn(StepResult.failure("stock locked"));

        RefundSagaState state = orchestrator.startRefund("order-1", "size", null);

        assertThat(state.getStatus()).isEqualTo(RefundSagaStatus.COMPENSATED);
        verify(reversePaymentStep, times(1)).compensate(any());
        verify(validateStep, never()).compensate(any());
        verify(updateOrderStep, never()).execute(any());
        verify(notifyRefundStep, never()).execute(any());
    }

    @Test
    void should_compensateInReverseOrder_whenUpdateOrderFails() {
        when(validateStep.execute(any())).thenReturn(StepResult.ok());
        when(reversePaymentStep.execute(any())).thenReturn(StepResult.ok());
        when(restoreInventoryStep.execute(any())).thenReturn(StepResult.ok());
        when(updateOrderStep.execute(any())).thenReturn(StepResult.failure("DB error"));

        RefundSagaState state = orchestrator.startRefund("order-1", "size", null);

        assertThat(state.getStatus()).isEqualTo(RefundSagaStatus.COMPENSATED);
        verify(restoreInventoryStep, times(1)).compensate(any());
        verify(reversePaymentStep, times(1)).compensate(any());
        verify(notifyRefundStep, never()).execute(any());
    }

    @Test
    void should_treatStepException_asFailure() {
        when(validateStep.execute(any())).thenReturn(StepResult.ok());
        when(reversePaymentStep.execute(any())).thenThrow(new RuntimeException("boom"));

        RefundSagaState state = orchestrator.startRefund("order-1", "size", null);

        assertThat(state.getStatus()).isEqualTo(RefundSagaStatus.FAILED);
        assertThat(state.getFailureReason()).contains("boom");
    }

    @Test
    void resume_should_skipAlreadyCompletedSteps() {
        // Saga already finished VALIDATE and REVERSE_PAYMENT.
        RefundSagaState state = RefundSagaState.builder()
            .id("s1").orderId("o1")
            .status(RefundSagaStatus.IN_PROGRESS)
            .currentStep(RefundSagaStep.RESTORE_INVENTORY)
            .completedSteps(EnumSet.of(RefundSagaStep.VALIDATE, RefundSagaStep.REVERSE_PAYMENT))
            .build();
        when(restoreInventoryStep.execute(any())).thenReturn(StepResult.ok());
        when(updateOrderStep.execute(any())).thenReturn(StepResult.ok());
        when(notifyRefundStep.execute(any())).thenReturn(StepResult.ok());

        RefundSagaState result = orchestrator.resume(state);

        assertThat(result.getStatus()).isEqualTo(RefundSagaStatus.COMPLETED);
        verify(validateStep, never()).execute(any());
        verify(reversePaymentStep, never()).execute(any());
        verify(restoreInventoryStep).execute(any());
        verify(updateOrderStep).execute(any());
        verify(notifyRefundStep).execute(any());
    }

    @Test
    void findSaga_should_delegateToRepository() {
        RefundSagaState state = RefundSagaState.builder().id("s1").build();
        when(sagaRepository.findById("s1")).thenReturn(Optional.of(state));

        Optional<RefundSagaState> found = orchestrator.findSaga("s1");

        assertThat(found).contains(state);
    }

    @Test
    void startRefund_should_persistOverrideAndFee_onState() {
        when(validateStep.execute(any())).thenReturn(StepResult.ok());
        when(reversePaymentStep.execute(any())).thenReturn(StepResult.ok());
        when(restoreInventoryStep.execute(any())).thenReturn(StepResult.ok());
        when(updateOrderStep.execute(any())).thenReturn(StepResult.ok());
        when(notifyRefundStep.execute(any())).thenReturn(StepResult.ok());

        RefundSagaState state = orchestrator.startRefund("order-1", "RMA-1", "u@x.com",
            new java.math.BigDecimal("60.00"), new java.math.BigDecimal("10"));

        assertThat(state.getRefundAmountOverride()).isEqualByComparingTo("60.00");
        assertThat(state.getRestockingFeePercent()).isEqualByComparingTo("10");
    }

    @Test
    void startRefund_should_rejectFeeAbove100() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> orchestrator.startRefund("order-1", "RMA-1", "u@x.com",
                null, new java.math.BigDecimal("101")));
    }

    @Test
    void startRefund_should_rejectNegativeFee() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> orchestrator.startRefund("order-1", "RMA-1", "u@x.com",
                null, new java.math.BigDecimal("-5")));
    }

    @Test
    void run_should_throw_whenStateMissing() {
        RefundSagaContext ctx = RefundSagaContext.builder().orderId("o1").build();
        try {
            orchestrator.run(ctx);
            assertThat(false).isTrue();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("state");
        }
    }
}
