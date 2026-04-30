package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.kafka.event.OrderEvent;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order Event Consumer
 * Listens to order-related Kafka events and triggers notifications.
 *
 * SECURITY NOTE — Kafka replay / duplicate-event protection:
 * Each handler checks the notification log before sending. If a SENT, PENDING, or RETRYING
 * record already exists for the same (orderId, templateCode) pair, the event is a replay
 * (either from at-least-once delivery or a malicious producer) and is silently dropped.
 * This prevents spam via Kafka topic re-injection.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    private static final List<NotificationStatus> ACTIVE_STATUSES =
            List.of(NotificationStatus.SENT, NotificationStatus.PENDING, NotificationStatus.RETRYING);

    @KafkaListener(topics = "order.created", groupId = "notification-service")
    public void handleOrderCreated(String message) {
        try {
            log.info("Received order.created event");

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            if (isDuplicate(event.getOrderId(), "ORDER_CONFIRMATION")) {
                log.warn("Duplicate order.created event for orderId={}, skipping", event.getOrderId());
                return;
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("totalAmount", event.getTotalAmount());
            variables.put("shippingAddress", event.getShippingAddress());

            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    "ORDER_CONFIRMATION",
                    variables,
                    event.getOrderId(),
                    "ORDER"
            );

            log.info("Order confirmation notification triggered for order: {}", event.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to process order.created event", e);
        }
    }

    @KafkaListener(topics = "payment.completed", groupId = "notification-service")
    public void handlePaymentCompleted(String message) {
        try {
            log.info("Received payment.completed event");

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            if (isDuplicate(event.getOrderId(), "PAYMENT_RECEIPT")) {
                log.warn("Duplicate payment.completed event for orderId={}, skipping", event.getOrderId());
                return;
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("totalAmount", event.getTotalAmount());

            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    "PAYMENT_RECEIPT",
                    variables,
                    event.getOrderId(),
                    "PAYMENT"
            );

            log.info("Payment receipt notification triggered for order: {}", event.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to process payment.completed event", e);
        }
    }

    @KafkaListener(topics = "order.shipped", groupId = "notification-service")
    public void handleOrderShipped(String message) {
        try {
            log.info("Received order.shipped event");

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            if (isDuplicate(event.getOrderId(), "SHIPPING_NOTIFICATION")) {
                log.warn("Duplicate order.shipped event for orderId={}, skipping", event.getOrderId());
                return;
            }

            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("shippingAddress", event.getShippingAddress());

            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    "SHIPPING_NOTIFICATION",
                    variables,
                    event.getOrderId(),
                    "SHIPMENT"
            );

            log.info("Shipping notification triggered for order: {}", event.getOrderNumber());

        } catch (Exception e) {
            log.error("Failed to process order.shipped event", e);
        }
    }

    private boolean isDuplicate(String entityId, String templateCode) {
        return notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
                entityId, templateCode, ACTIVE_STATUSES);
    }
}
