package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.RefundSagaStep;
import com.ecommerce.orderservice.saga.refund.SagaStep;
import com.ecommerce.orderservice.saga.refund.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateRefundStep implements SagaStep {

    public static final int REFUND_WINDOW_DAYS = 30;

    private static final Set<OrderStatus> REFUNDABLE_STATUSES =
        Set.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Override
    public RefundSagaStep id() {
        return RefundSagaStep.VALIDATE;
    }

    @Override
    public StepResult execute(RefundSagaContext ctx) {
        Order order = orderRepository.findById(ctx.getOrderId()).orElse(null);
        if (order == null) {
            return StepResult.failure("Order not found: " + ctx.getOrderId());
        }

        if (order.getStatus() == OrderStatus.REFUNDED) {
            return StepResult.failure("Order already refunded");
        }

        boolean isDelivered = order.getStatus() == OrderStatus.DELIVERED;
        boolean isPaid = order.getPaymentIntentId() != null
            && REFUNDABLE_STATUSES.contains(order.getStatus());
        if (!isDelivered && !isPaid) {
            return StepResult.failure(
                "Order status is not refundable: " + order.getStatus());
        }

        if (order.getCreatedAt() == null
            || Duration.between(order.getCreatedAt(), LocalDateTime.now(clock))
                .toDays() > REFUND_WINDOW_DAYS) {
            return StepResult.failure("Refund window has expired");
        }

        ctx.setOrder(order);
        ctx.setUserId(order.getUserId());
        ctx.setOrderNumber(order.getOrderNumber());
        ctx.setPaymentIntentId(order.getPaymentIntentId());
        ctx.setRefundAmount(order.getTotal());
        log.info("Refund validation OK for order {}", order.getOrderNumber());
        return StepResult.ok();
    }
}
