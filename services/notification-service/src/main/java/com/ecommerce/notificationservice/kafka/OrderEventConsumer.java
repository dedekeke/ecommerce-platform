package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.event.OrderEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Order Event Consumer
 * Listens to order-related Kafka events and triggers notifications
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.created", groupId = "notification-service")
    public void handleOrderCreated(String message) {
        try {
            log.info("Received order.created event: {}", message);

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            // Prepare template variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("totalAmount", event.getTotalAmount());
            variables.put("shippingAddress", event.getShippingAddress());

            // Send order confirmation email
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
            log.info("Received payment.completed event: {}", message);

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            // Prepare template variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("totalAmount", event.getTotalAmount());

            // Send payment receipt email
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
            log.info("Received order.shipped event: {}", message);

            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);

            // Prepare template variables
            Map<String, Object> variables = new HashMap<>();
            variables.put("orderNumber", event.getOrderNumber());
            variables.put("userName", event.getUserName());
            variables.put("shippingAddress", event.getShippingAddress());

            // Send shipping notification email
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
}
