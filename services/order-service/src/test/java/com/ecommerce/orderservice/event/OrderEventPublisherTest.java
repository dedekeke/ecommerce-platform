package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the publisher emits payloads compatible with notification-service
 * (userEmail, userName, totalAmount, shippingAddress as flat string).
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    private OrderEventPublisher publisher;

    private Order order;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);

        Address address = Address.builder()
            .street("123 Main St")
            .city("Springfield")
            .state("IL")
            .postalCode("62704")
            .country("USA")
            .build();

        OrderItem item = OrderItem.builder()
            .productId("p1")
            .productName("Widget")
            .price(BigDecimal.valueOf(10))
            .quantity(2)
            .subtotal(BigDecimal.valueOf(20))
            .build();

        List<OrderItem> items = new ArrayList<>();
        items.add(item);

        order = Order.builder()
            .id("order-id-1")
            .orderNumber("ORD-2026-00001")
            .userId("user-1")
            .items(items)
            .subtotal(BigDecimal.valueOf(20))
            .tax(BigDecimal.valueOf(1.60))
            .shippingCost(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(21.60))
            .status(OrderStatus.PENDING)
            .shippingAddress(address)
            .build();
    }

    @Test
    void should_populateNotificationFields_when_publishingOrderCreatedWithRecipient() {
        when(kafkaTemplate.send(eq("order.created"), eq("order-id-1"), any(OrderEvent.class)))
            .thenReturn(new CompletableFuture<>());

        publisher.publishOrderCreatedEvent(order, "jane@example.com", "Jane Doe");

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("order.created"), eq("order-id-1"), eventCaptor.capture());

        OrderEvent published = eventCaptor.getValue();
        assertThat(published.getUserEmail()).isEqualTo("jane@example.com");
        assertThat(published.getUserName()).isEqualTo("Jane Doe");
        assertThat(published.getTotalAmount()).isEqualByComparingTo("21.60");
        assertThat(published.getShippingAddress())
            .contains("123 Main St")
            .contains("Springfield")
            .contains("IL")
            .contains("62704")
            .contains("USA");
        assertThat(published.getOrderNumber()).isEqualTo("ORD-2026-00001");
        assertThat(published.getUserId()).isEqualTo("user-1");
    }

    @Test
    void should_fallbackToBlankRecipient_when_emailAndNameNotProvided() {
        when(kafkaTemplate.send(eq("order.created"), eq("order-id-1"), any(OrderEvent.class)))
            .thenReturn(new CompletableFuture<>());

        publisher.publishOrderCreatedEvent(order);

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq("order.created"), eq("order-id-1"), eventCaptor.capture());

        OrderEvent published = eventCaptor.getValue();
        assertThat(published.getOrderNumber()).isEqualTo("ORD-2026-00001");
        assertThat(published.getUserEmail()).isNull();
        assertThat(published.getUserName()).isNull();
        assertThat(published.getTotalAmount()).isEqualByComparingTo("21.60");
    }

    @Test
    void should_swallowKafkaException_when_sendFails() {
        when(kafkaTemplate.send(eq("order.created"), eq("order-id-1"), any(OrderEvent.class)))
            .thenThrow(new RuntimeException("broker down"));

        publisher.publishOrderCreatedEvent(order, "x@y.z", "X");

        verify(kafkaTemplate).send(eq("order.created"), eq("order-id-1"), any(OrderEvent.class));
    }
}
