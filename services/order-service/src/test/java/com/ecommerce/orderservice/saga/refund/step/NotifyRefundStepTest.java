package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.saga.refund.RefundCompletedEvent;
import com.ecommerce.orderservice.saga.refund.RefundEventPublisher;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotifyRefundStepTest {

    @Mock
    private RefundEventPublisher publisher;

    private NotifyRefundStep step;

    @BeforeEach
    void setUp() {
        step = new NotifyRefundStep(publisher);
    }

    @Test
    void should_publishRefundCompletedEvent_withSagaContextFields() {
        RefundSagaState state = RefundSagaState.builder().id("saga-1").build();
        RefundSagaContext ctx = RefundSagaContext.builder()
            .state(state).orderId("o1").orderNumber("ORD-1")
            .userId("u1").userEmail("buyer@example.com")
            .refundTransactionId("ref-9").refundAmount(new BigDecimal("42.00"))
            .build();

        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        ArgumentCaptor<RefundCompletedEvent> captor = ArgumentCaptor.forClass(RefundCompletedEvent.class);
        verify(publisher).publishRefundCompleted(captor.capture());
        RefundCompletedEvent event = captor.getValue();
        assertThat(event.getSagaId()).isEqualTo("saga-1");
        assertThat(event.getOrderId()).isEqualTo("o1");
        assertThat(event.getUserEmail()).isEqualTo("buyer@example.com");
        assertThat(event.getRefundTransactionId()).isEqualTo("ref-9");
        assertThat(event.getAmount()).isEqualTo(new BigDecimal("42.00"));
    }

    @Test
    void should_returnSuccess_evenWhenPublisherThrows() {
        doThrow(new RuntimeException("kafka down"))
            .when(publisher).publishRefundCompleted(org.mockito.ArgumentMatchers.any());

        RefundSagaContext ctx = RefundSagaContext.builder().orderId("o1").build();
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
    }
}
