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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        BigDecimal refundAmount = computeRefundAmount(ctx, order);
        ctx.setRefundAmount(refundAmount);
        // Persist on the saga state so a resumed run (which skips this step)
        // still has the amount for the payment-reversal / compensation steps.
        if (ctx.getState() != null) {
            ctx.getState().setRefundAmount(refundAmount);
        }
        log.info("Refund validation OK for order {} — amount {}",
            order.getOrderNumber(), refundAmount);
        return StepResult.ok();
    }

    /**
     * Refund base is the override (partial-return approved-line total) when
     * present, else the full order total. A restocking fee percentage, if
     * set, is then deducted. The result is clamped to non-negative and scaled
     * to 2 decimals.
     */
    private BigDecimal computeRefundAmount(RefundSagaContext ctx, Order order) {
        BigDecimal base = ctx.getRefundAmountOverride() != null
            ? ctx.getRefundAmountOverride()
            : order.getTotal();
        if (base == null) {
            base = BigDecimal.ZERO;
        }

        BigDecimal fee = ctx.getRestockingFeePercent();
        if (fee != null && fee.signum() > 0) {
            BigDecimal keepRatio = BigDecimal.ONE.subtract(
                fee.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            base = base.multiply(keepRatio);
        }
        return base.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
