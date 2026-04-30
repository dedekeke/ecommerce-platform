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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateOrderStep implements SagaStep {

    private final OrderRepository orderRepository;

    @Override
    public RefundSagaStep id() {
        return RefundSagaStep.UPDATE_ORDER;
    }

    @Override
    public boolean hasCompensation() {
        return true;
    }

    @Override
    @Transactional
    public StepResult execute(RefundSagaContext ctx) {
        Order order = orderRepository.findById(ctx.getOrderId()).orElse(null);
        if (order == null) {
            return StepResult.failure("Order disappeared during refund: " + ctx.getOrderId());
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.info("Order {} already REFUNDED — idempotent re-run", order.getId());
            return StepResult.ok();
        }
        try {
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
            ctx.setOrder(order);
            log.info("Order {} marked REFUNDED", order.getOrderNumber());
            return StepResult.ok();
        } catch (Exception e) {
            log.error("Failed to mark order REFUNDED", e);
            return StepResult.failure("Order update failed: " + e.getMessage());
        }
    }

    /**
     * Compensation: revert to DELIVERED when the saga fails after we marked
     * the order REFUNDED. In a richer state machine you might restore the
     * exact prior status; for the sample we use DELIVERED as the canonical
     * pre-refund state.
     */
    @Override
    @Transactional
    public void compensate(RefundSagaContext ctx) {
        try {
            Order order = orderRepository.findById(ctx.getOrderId()).orElse(null);
            if (order == null) {
                log.warn("[COMPENSATE] Order {} not found", ctx.getOrderId());
                return;
            }
            if (order.getStatus() == OrderStatus.REFUNDED) {
                order.setStatus(OrderStatus.DELIVERED);
                orderRepository.save(order);
                log.warn("[COMPENSATE] Order {} reverted REFUNDED -> DELIVERED",
                    order.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("Failed to compensate order status update", e);
        }
    }
}
