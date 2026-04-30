package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateOrderStepTest {

    @Mock
    private OrderRepository orderRepository;

    private UpdateOrderStep step;

    @BeforeEach
    void setUp() {
        step = new UpdateOrderStep(orderRepository);
    }

    @Test
    void should_markOrderRefunded_whenInDeliveredState() {
        Order order = new Order();
        order.setId("o1");
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        StepResult result = step.execute(RefundSagaContext.builder().orderId("o1").build());

        assertThat(result.successful()).isTrue();
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void should_beIdempotent_whenOrderAlreadyRefunded() {
        Order order = new Order();
        order.setId("o1");
        order.setStatus(OrderStatus.REFUNDED);
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        StepResult result = step.execute(RefundSagaContext.builder().orderId("o1").build());

        assertThat(result.successful()).isTrue();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void should_fail_whenOrderMissing() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        StepResult result = step.execute(RefundSagaContext.builder().orderId("missing").build());

        assertThat(result.successful()).isFalse();
    }

    @Test
    void compensate_should_revertRefundedToDelivered() {
        Order order = new Order();
        order.setId("o1");
        order.setStatus(OrderStatus.REFUNDED);
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        step.compensate(RefundSagaContext.builder().orderId("o1").build());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void compensate_should_skip_whenOrderAlreadyNotRefunded() {
        Order order = new Order();
        order.setId("o1");
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        step.compensate(RefundSagaContext.builder().orderId("o1").build());

        verify(orderRepository, never()).save(any());
    }
}
