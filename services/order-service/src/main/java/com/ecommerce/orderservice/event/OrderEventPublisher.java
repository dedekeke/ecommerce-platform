package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Publisher for order-related events to Kafka
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_UPDATED_TOPIC = "order.updated";
    private static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    private static final String ORDER_COMPLETED_TOPIC = "order.completed";

    /**
     * Publish order created event without recipient details.
     */
    public void publishOrderCreatedEvent(Order order) {
        publishOrderCreatedEvent(order, null, null);
    }

    /**
     * Publish order created event with recipient email/name so the
     * notification-service consumer can render the order confirmation email.
     */
    public void publishOrderCreatedEvent(Order order, String userEmail, String userName) {
        OrderEvent event = buildOrderEvent(order, "ORDER_CREATED");
        event.setUserEmail(userEmail);
        event.setUserName(userName);
        publishEvent(ORDER_CREATED_TOPIC, order.getId(), event);
        log.info("Published ORDER_CREATED event for order: {}", order.getOrderNumber());
    }

    /**
     * Publish order updated event
     */
    public void publishOrderUpdatedEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_UPDATED");
        publishEvent(ORDER_UPDATED_TOPIC, order.getId(), event);
        log.info("Published ORDER_UPDATED event for order: {}", order.getOrderNumber());
    }

    /**
     * Publish order cancelled event
     */
    public void publishOrderCancelledEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_CANCELLED");
        publishEvent(ORDER_CANCELLED_TOPIC, order.getId(), event);
        log.info("Published ORDER_CANCELLED event for order: {}", order.getOrderNumber());
    }

    /**
     * Publish order completed event
     */
    public void publishOrderCompletedEvent(Order order) {
        OrderEvent event = buildOrderEvent(order, "ORDER_COMPLETED");
        publishEvent(ORDER_COMPLETED_TOPIC, order.getId(), event);
        log.info("Published ORDER_COMPLETED event for order: {}", order.getOrderNumber());
    }

    /**
     * Build order event from order entity
     */
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

    /**
     * Render the structured Address as a single line for email templates
     * which expect a flat string.
     */
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

    /**
     * Build order item event from order item entity
     */
    private OrderEvent.OrderItemEvent buildOrderItemEvent(OrderItem item) {
        return OrderEvent.OrderItemEvent.builder()
            .productId(item.getProductId())
            .productName(item.getProductName())
            .price(item.getPrice())
            .quantity(item.getQuantity())
            .subtotal(item.getSubtotal())
            .build();
    }

    /**
     * Publish event to Kafka topic
     */
    private void publishEvent(String topic, String key, OrderEvent event) {
        try {
            kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to topic: {}", topic, ex);
                    } else {
                        log.debug("Event published successfully to topic: {} with key: {}", topic, key);
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing event to Kafka", e);
        }
    }
}
