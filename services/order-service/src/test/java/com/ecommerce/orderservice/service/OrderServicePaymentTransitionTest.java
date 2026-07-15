package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.PromotionServiceClient;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the payment-driven state transitions and their precedence
 * rules on {@link OrderService}.
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePaymentTransitionTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderNumberGeneratorService orderNumberGenerator;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderService orderService;

    private static final String ORDER_ID = "order-1";

    // ---- confirmOrderPaid ----

    @Test
    void should_confirmAndEmitUpdated_when_pendingOrderPaid() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        boolean applied = orderService.confirmOrderPaid(ORDER_ID);

        assertThat(applied).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventPublisher).publishOrderUpdatedEvent(order);
    }

    @Test
    void should_beNoop_when_confirmingAlreadyConfirmedOrder() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        boolean applied = orderService.confirmOrderPaid(ORDER_ID);

        assertThat(applied).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderUpdatedEvent(any());
    }

    @Test
    void should_notResurrect_when_confirmingCancelledOrder() {
        Order order = orderIn(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        boolean applied = orderService.confirmOrderPaid(ORDER_ID);

        assertThat(applied).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventPublisher, never()).publishOrderUpdatedEvent(any());
    }

    @Test
    void should_returnFalse_when_confirmingUnknownOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(orderService.confirmOrderPaid(ORDER_ID)).isFalse();
    }

    // ---- failOrderPayment ----

    @Test
    void should_cancelAndSignalRelease_when_pendingOrderPaymentFailed() {
        Order order = orderIn(OrderStatus.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        boolean releaseNeeded = orderService.failOrderPayment(ORDER_ID);

        assertThat(releaseNeeded).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventPublisher).publishOrderCancelledEvent(order);
    }

    @Test
    void should_notDowngrade_when_paymentFailedAgainstConfirmedOrder() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        boolean releaseNeeded = orderService.failOrderPayment(ORDER_ID);

        assertThat(releaseNeeded).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderEventPublisher, never()).publishOrderCancelledEvent(any());
    }

    @Test
    void should_beNoop_when_paymentFailedAgainstCancelledOrder() {
        Order order = orderIn(OrderStatus.CANCELLED);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        boolean releaseNeeded = orderService.failOrderPayment(ORDER_ID);

        assertThat(releaseNeeded).isFalse();
        verify(orderEventPublisher, never()).publishOrderCancelledEvent(any());
    }

    @Test
    void should_returnFalse_when_failingUnknownOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(orderService.failOrderPayment(ORDER_ID)).isFalse();
    }

    private Order orderIn(OrderStatus status) {
        return Order.builder()
            .id(ORDER_ID)
            .orderNumber("ORD-1")
            .userId("user-1")
            .subtotal(new BigDecimal("10.00"))
            .tax(new BigDecimal("0.80"))
            .shippingCost(BigDecimal.ZERO)
            .total(new BigDecimal("10.80"))
            .status(status)
            .shippingAddress(Address.builder()
                .street("1 Main St").city("SF").state("CA")
                .postalCode("94105").country("USA").build())
            .build();
    }
}
