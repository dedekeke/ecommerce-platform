package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.OrderEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Order Event Consumer
 * Listens to order-related Kafka events and triggers notifications.
 *
 * <p>Kafka replay / duplicate-event protection: each handler stakes an
 * insert-first claim on a {@code <templateCode>:<orderId>} dedup key via
 * {@link NotificationEventDeduplicator} before sending. Exactly one delivery of
 * a given (template, order) wins the claim; a redelivery (at-least-once
 * delivery, a concurrent replica, or a malicious re-inject) loses on the unique
 * {@code _id} index and is dropped. This replaces the previous check-then-insert
 * against the notification log, which raced under concurrency and could emit
 * duplicate emails.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    static final String ORDER_CREATED_TOPIC = "order.created";
    static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    static final String ORDER_SHIPPED_TOPIC = "order.shipped";

    private final NotificationService notificationService;
    private final NotificationEventDeduplicator deduplicator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = ORDER_CREATED_TOPIC, groupId = "notification-service")
    public void handleOrderCreated(String message) {
        OrderEvent event = parse(message, ORDER_CREATED_TOPIC);
        if (event == null) {
            return;
        }

        if (!deduplicator.claim(dedupKey("ORDER_CONFIRMATION", event.getOrderId()), ORDER_CREATED_TOPIC)) {
            log.warn("Duplicate order.created event for orderId={}, skipping", event.getOrderId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNumber", event.getOrderNumber());
        variables.put("userName", event.getUserName());
        variables.put("totalAmount", event.getTotalAmount());
        variables.put("shippingAddress", event.getShippingAddress());

        send(event, event.getUserEmail(), "ORDER_CONFIRMATION", "ORDER", variables);
        dispatchAncillaryChannels(event, "ORDER_CONFIRMATION_SMS", "ORDER_CONFIRMATION_PUSH", "ORDER", variables);
        log.info("Order confirmation notification triggered for order: {}", event.getOrderNumber());
    }

    @KafkaListener(topics = PAYMENT_COMPLETED_TOPIC, groupId = "notification-service")
    public void handlePaymentCompleted(String message) {
        OrderEvent event = parse(message, PAYMENT_COMPLETED_TOPIC);
        if (event == null) {
            return;
        }

        if (!deduplicator.claim(dedupKey("PAYMENT_RECEIPT", event.getOrderId()), PAYMENT_COMPLETED_TOPIC)) {
            log.warn("Duplicate payment.completed event for orderId={}, skipping", event.getOrderId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNumber", event.getOrderNumber());
        variables.put("userName", event.getUserName());
        variables.put("totalAmount", event.getTotalAmount());

        send(event, event.getUserEmail(), "PAYMENT_RECEIPT", "PAYMENT", variables);
        log.info("Payment receipt notification triggered for order: {}", event.getOrderNumber());
    }

    @KafkaListener(topics = ORDER_SHIPPED_TOPIC, groupId = "notification-service")
    public void handleOrderShipped(String message) {
        OrderEvent event = parse(message, ORDER_SHIPPED_TOPIC);
        if (event == null) {
            return;
        }

        if (!deduplicator.claim(dedupKey("SHIPPING_NOTIFICATION", event.getOrderId()), ORDER_SHIPPED_TOPIC)) {
            log.warn("Duplicate order.shipped event for orderId={}, skipping", event.getOrderId());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderNumber", event.getOrderNumber());
        variables.put("userName", event.getUserName());
        variables.put("shippingAddress", event.getShippingAddress());
        variables.put("carrier", event.getCarrier());
        variables.put("trackingNumber", event.getTrackingNumber());

        send(event, event.getUserEmail(), "SHIPPING_NOTIFICATION", "SHIPMENT", variables);
        dispatchAncillaryChannels(event, "SHIPPING_NOTIFICATION_SMS", "SHIPPING_NOTIFICATION_PUSH", "SHIPMENT", variables);
        log.info("Shipping notification triggered for order: {}", event.getOrderNumber());
    }

    private OrderEvent parse(String message, String topic) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            if (event == null || event.getOrderId() == null) {
                log.warn("Discarding {} event with missing orderId", topic);
                return null;
            }
            log.info("Received {} event for orderId={}", topic, event.getOrderId());
            return event;
        } catch (Exception e) {
            log.error("Failed to deserialize {} event", topic, e);
            return null;
        }
    }

    /**
     * Fan out to the SMS and push channels in addition to email, but only when the event carries a
     * recipient phone / device token. When both are absent the notification degrades to email-only.
     * The handler-level dedup claim already guards against replays, so no extra dedup is needed here.
     */
    private void dispatchAncillaryChannels(OrderEvent event, String smsTemplate, String pushTemplate,
                                           String entityType, Map<String, Object> variables) {
        if (StringUtils.hasText(event.getUserPhone())) {
            send(event, event.getUserPhone(), smsTemplate, entityType, variables);
        }
        if (StringUtils.hasText(event.getUserDeviceToken())) {
            send(event, event.getUserDeviceToken(), pushTemplate, entityType, variables);
        }
    }

    private void send(OrderEvent event, String recipient, String templateCode, String entityType,
                      Map<String, Object> variables) {
        try {
            notificationService.sendNotification(
                    event.getUserId(), recipient, templateCode, variables,
                    event.getOrderId(), entityType);
        } catch (Exception e) {
            log.error("Failed to dispatch {} notification for orderId={}", templateCode, event.getOrderId(), e);
        }
    }

    private static String dedupKey(String templateCode, String orderId) {
        return templateCode + ":" + orderId;
    }
}
