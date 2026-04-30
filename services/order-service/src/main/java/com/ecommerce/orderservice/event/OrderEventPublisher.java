package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Publisher for order-related events.
 *
 * <p>Refactored to use the transactional outbox pattern. Public API is
 * unchanged so callers (saga, OrderService) continue to compile and behave
 * identically — but instead of calling {@code KafkaTemplate.send(...)} (the
 * dual-write that could lose events on crash between DB commit and broker
 * ack) we now record the event in the {@code outbox_event} table inside the
 * caller's existing transaction. The {@link OutboxService} requires the
 * caller to already be transactional ({@code Propagation.MANDATORY}) so we
 * fail loudly if a caller bypasses the saga / service layer.
 *
 * <p>The {@link com.ecommerce.orderservice.outbox.OutboxRelay} ships rows
 * to Kafka asynchronously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OutboxService outboxService;

    private static final String AGGREGATE_TYPE = "Order";

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_UPDATED_TOPIC = "order.updated";
    private static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    private static final String ORDER_COMPLETED_TOPIC = "order.completed";

    public void publishOrderCreatedEvent(Order order) {
        publishOrderCreatedEvent(order, null, null);
    }

    public void publishOrderCreatedEvent(Order order, String userEmail, String userName) {
        OrderEvent event = buildOrderEvent(order, "ORDER_CREATED");
        event.setUserEmail(userEmail);
        event.setUserName(userName);
        record(ORDER_CREATED_TOPIC, "ORDER_CREATED", order.getId(), event);
        log.info("Recorded ORDER_CREATED outbox event for order: {}", order.getOrderNumber());
    }

    public void publishOrderUpdatedEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_UPDATED");
        record(ORDER_UPDATED_TOPIC, "ORDER_UPDATED", order.getId(), event);
        log.info("Recorded ORDER_UPDATED outbox event for order: {}", order.getOrderNumber());
    }

    public void publishOrderCancelledEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_CANCELLED");
        record(ORDER_CANCELLED_TOPIC, "ORDER_CANCELLED", order.getId(), event);
        log.info("Recorded ORDER_CANCELLED outbox event for order: {}", order.getOrderNumber());
    }

    public void publishOrderCompletedEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_COMPLETED");
        record(ORDER_COMPLETED_TOPIC, "ORDER_COMPLETED", order.getId(), event);
        log.info("Recorded ORDER_COMPLETED outbox event for order: {}", order.getOrderNumber());
    }

    private OrderEvent buildOrderEvent(Order order, String eventType) {
        return OrderEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .userId(order.getUserId())
            .items(order.getItems().stream()
                .map(this::buildOrderItemEvent)
                .collect(Collectors.toList()))
            .total(order.getTotal())
            .totalAmount(order.getTotal())
            .status(order.getStatus())
            .paymentIntentId(order.getPaymentIntentId())
            .shippingAddress(formatShippingAddress(order.getShippingAddress()))
            .build();
    }

    private String formatShippingAddress(Address address) {
        if (address == null) {
            return null;
        }
        String line = Stream.of(
                address.getStreet(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry())
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.joining(", "));
        return line.isBlank() ? null : line;
    }

    private OrderEvent.OrderItemEvent buildOrderItemEvent(OrderItem item) {
        return OrderEvent.OrderItemEvent.builder()
            .productId(item.getProductId())
            .productName(item.getProductName())
            .price(item.getPrice())
            .quantity(item.getQuantity())
            .subtotal(item.getSubtotal())
            .build();
    }

    private void record(String topic, String eventType, String aggregateId, OrderEvent event) {
        try {
            outboxService.recordEvent(AGGREGATE_TYPE, aggregateId, eventType, topic, event);
        } catch (RuntimeException e) {
            // Re-throw: the caller's transaction MUST roll back if we cannot
            // record the event — that is the entire point of the outbox.
            log.error("Failed to record outbox event {} for aggregate {}", eventType, aggregateId, e);
            throw e;
        }
    }
}
