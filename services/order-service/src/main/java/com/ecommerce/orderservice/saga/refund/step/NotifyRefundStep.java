package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.saga.refund.RefundCompletedEvent;
import com.ecommerce.orderservice.saga.refund.RefundEventPublisher;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.RefundSagaStep;
import com.ecommerce.orderservice.saga.refund.SagaStep;
import com.ecommerce.orderservice.saga.refund.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRefundStep implements SagaStep {

    private final RefundEventPublisher refundEventPublisher;

    @Override
    public RefundSagaStep id() {
        return RefundSagaStep.NOTIFY;
    }

    @Override
    public StepResult execute(RefundSagaContext ctx) {
        try {
            RefundCompletedEvent event = RefundCompletedEvent.builder()
                .sagaId(ctx.getState() != null ? ctx.getState().getId() : null)
                .orderId(ctx.getOrderId())
                .orderNumber(ctx.getOrderNumber())
                .userId(ctx.getUserId())
                .userEmail(ctx.getUserEmail())
                .refundTransactionId(ctx.getRefundTransactionId())
                .amount(ctx.getRefundAmount())
                .completedAt(LocalDateTime.now())
                .build();
            refundEventPublisher.publishRefundCompleted(event);
            return StepResult.ok();
        } catch (Exception e) {
            // Notification is best-effort; log but don't fail the saga.
            log.warn("Notification publish failed for order {} — saga still successful",
                ctx.getOrderId(), e);
            return StepResult.ok();
        }
    }
}
