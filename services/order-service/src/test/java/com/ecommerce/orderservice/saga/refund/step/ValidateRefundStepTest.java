package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateRefundStepTest {

    @Mock
    private OrderRepository orderRepository;

    private Clock fixedClock;
    private ValidateRefundStep step;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
            Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
        step = new ValidateRefundStep(orderRepository, fixedClock);
    }

    @Test
    void should_succeed_when_orderDeliveredWithinWindow() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(5));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        RefundSagaContext ctx = ctxFor("o1");
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        assertThat(ctx.getOrder()).isSameAs(order);
        assertThat(ctx.getRefundAmount()).isEqualTo(order.getTotal());
    }

    @Test
    void should_succeed_when_orderPaidNotYetDelivered() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.PROCESSING);
        order.setPaymentIntentId("pi_123");
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(2));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        StepResult result = step.execute(ctxFor("o1"));

        assertThat(result.successful()).isTrue();
    }

    @Test
    void should_fail_when_orderNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        StepResult result = step.execute(ctxFor("missing"));

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("not found");
    }

    @Test
    void should_fail_when_orderAlreadyRefunded() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.REFUNDED);
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        StepResult result = step.execute(ctxFor("o1"));

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("already refunded");
    }

    @Test
    void should_fail_when_orderInPendingOrCancelled() {
        Order pending = baseOrder();
        pending.setStatus(OrderStatus.PENDING);
        pending.setCreatedAt(LocalDateTime.now(fixedClock));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(pending));

        StepResult result = step.execute(ctxFor("o1"));

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("not refundable");
    }

    @Test
    void should_fail_when_outsideRefundWindow() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(31));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        StepResult result = step.execute(ctxFor("o1"));

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("window");
    }

    @Test
    void should_useOverrideAmount_when_refundAmountOverrideSet() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(5));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1")
            .refundAmountOverride(new BigDecimal("40.00"))
            .build();
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        assertThat(ctx.getRefundAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void should_deductRestockingFee_when_feePercentSet() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(5));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        // 20% fee on the full 100.00 total -> 80.00 refunded.
        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1")
            .restockingFeePercent(new BigDecimal("20"))
            .build();
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        assertThat(ctx.getRefundAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void should_applyFeeToOverrideBase_when_bothSet() {
        Order order = baseOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setCreatedAt(LocalDateTime.now(fixedClock).minusDays(5));
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        // 10% fee on a 60.00 partial base -> 54.00.
        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1")
            .refundAmountOverride(new BigDecimal("60.00"))
            .restockingFeePercent(new BigDecimal("10"))
            .build();
        StepResult result = step.execute(ctx);

        assertThat(ctx.getRefundAmount()).isEqualByComparingTo("54.00");
    }

    @Test
    void compensate_isNoOp() {
        // No-op compensation must not throw even with a half-built context.
        step.compensate(RefundSagaContext.builder().build());
    }

    private RefundSagaContext ctxFor(String orderId) {
        return RefundSagaContext.builder().orderId(orderId).build();
    }

    private Order baseOrder() {
        Order order = new Order();
        order.setId("o1");
        order.setOrderNumber("ORD-1");
        order.setUserId("u1");
        order.setTotal(new BigDecimal("100.00"));
        return order;
    }
}
