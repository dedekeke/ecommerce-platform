package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Verifies the publisher records outbox events with payloads compatible with
 * the notification-service consumer (userEmail, userName, totalAmount, flat
 * shippingAddress string) and propagates outbox failures so the surrounding
 * transaction rolls back.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private OrderEventPublisher publisher;

    private Order order;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(outboxService);

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
    void should_recordOutboxEventWithRecipientFields_when_publishingOrderCreatedWithRecipient() {
        publisher.publishOrderCreatedEvent(order, "jane@example.com", "Jane Doe");

        ArgumentCaptor<OrderEvent> payloadCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(outboxService).recordEvent(
            eq("Order"),
            eq("order-id-1"),
            eq("ORDER_CREATED"),
            eq("order.created"),
            payloadCaptor.capture()
        );

        OrderEvent published = payloadCaptor.getValue();
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
        assertThat(published.getEventType()).isEqualTo("ORDER_CREATED");
    }

    @Test
    void should_omitRecipientFields_when_emailAndNameNotProvided() {
        publisher.publishOrderCreatedEvent(order);

        ArgumentCaptor<OrderEvent> payloadCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(outboxService).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_CREATED"), eq("order.created"),
            payloadCaptor.capture()
        );

        OrderEvent published = payloadCaptor.getValue();
        assertThat(published.getOrderNumber()).isEqualTo("ORD-2026-00001");
        assertThat(published.getUserEmail()).isNull();
        assertThat(published.getUserName()).isNull();
        assertThat(published.getTotalAmount()).isEqualByComparingTo("21.60");
    }

    @Test
    void should_routeUpdatedEvent_when_publishingOrderUpdated() {
        publisher.publishOrderUpdatedEvent(order);

        verify(outboxService).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_UPDATED"), eq("order.updated"),
            any(OrderEvent.class)
        );
    }

    @Test
    void should_routeCancelledEvent_when_publishingOrderCancelled() {
        publisher.publishOrderCancelledEvent(order);

        verify(outboxService).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_CANCELLED"), eq("order.cancelled"),
            any(OrderEvent.class)
        );
    }

    @Test
    void should_routeCompletedEvent_when_publishingOrderCompleted() {
        publisher.publishOrderCompletedEvent(order);

        verify(outboxService).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_COMPLETED"), eq("order.completed"),
            any(OrderEvent.class)
        );
    }

    @Test
    void should_propagateException_when_outboxRecordFails() {
        // Outbox failures must surface so the caller's transaction rolls back —
        // the entire point of the pattern is "no commit without event".
        doThrow(new RuntimeException("DB down"))
            .when(outboxService).recordEvent(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> publisher.publishOrderCreatedEvent(order, "x@y.z", "X"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("DB down");
    }

    @Test
    void should_serialiseFlattenedAddress_when_shippingAddressBlankFieldsPresent() {
        Address sparse = Address.builder()
            .street("1 Sparse Ln")
            .city("")
            .state(null)
            .postalCode("00000")
            .country("USA")
            .build();
        order.setShippingAddress(sparse);

        publisher.publishOrderCreatedEvent(order);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(outboxService).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_CREATED"), eq("order.created"),
            captor.capture()
        );
        // Blank/null address fragments must not appear as ", ," in the rendered string.
        assertThat(captor.getValue().getShippingAddress())
            .isEqualTo("1 Sparse Ln, 00000, USA");
    }

    @Test
    void should_recordNothingTwice_when_calledOnce() {
        publisher.publishOrderCreatedEvent(order);
        // Defensive: verify exactly-one outbox write per call so we never
        // accidentally regress to double-emitting events.
        verify(outboxService, never()).recordEvent(
            eq("Order"), eq("order-id-1"), eq("ORDER_UPDATED"), eq("order.updated"),
            any(OrderEvent.class)
        );
    }

    @Test
    void should_doNothingOnOutbox_when_publisherNeverInvoked() {
        verifyNoInteractions(outboxService);
    }
}
