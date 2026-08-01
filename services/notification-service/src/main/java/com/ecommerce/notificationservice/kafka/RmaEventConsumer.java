package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RmaEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes the three customer-facing RMA topics and emails the customer.
 * {@code rma.received} is intentionally not handled here — it is an internal
 * warehouse event with no customer email.
 *
 * <p><b>Idempotency.</b> Enforced insert-first via
 * {@link NotificationEventDeduplicator} on {@code <templateCode>:<rmaNumber>}.
 * We key strictly on {@code rmaNumber}, which the order-service RMA saga
 * generates at request time and stores under a unique constraint, so it is
 * always present and globally unique. We deliberately do <em>not</em> fall back
 * to {@code orderId}: a customer with sequential returns on one order would
 * collide on an orderId key and have the second return's email suppressed. An
 * event missing {@code rmaNumber} is treated as malformed and skipped.
 *
 * <p><b>Delivery + retry.</b> Sends route through
 * {@link NotificationService#sendNotification} (the retry-capable path). A
 * transient email failure becomes a RETRYING log that
 * {@code NotificationRetryScheduler} re-sends internally, so the permanent
 * fire-once dedup claim is correct.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RmaEventConsumer {

    public static final String RMA_REQUESTED_TOPIC = "rma.requested";
    public static final String RMA_COMPLETED_TOPIC = "rma.completed";
    public static final String RMA_REJECTED_TOPIC = "rma.rejected";

    public static final String CODE_REQUESTED = "RMA_REQUESTED";
    public static final String CODE_COMPLETED = "RMA_COMPLETED";
    public static final String CODE_REJECTED = "RMA_REJECTED";

    public static final String ENTITY_TYPE = "RMA";

    private final NotificationService notificationService;
    private final NotificationEventDeduplicator deduplicator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = RMA_REQUESTED_TOPIC, groupId = "notification-service-rma-requested")
    public void handleRmaRequested(String message) {
        process(message, RMA_REQUESTED_TOPIC, CODE_REQUESTED);
    }

    @KafkaListener(topics = RMA_COMPLETED_TOPIC, groupId = "notification-service-rma-completed")
    public void handleRmaCompleted(String message) {
        process(message, RMA_COMPLETED_TOPIC, CODE_COMPLETED);
    }

    @KafkaListener(topics = RMA_REJECTED_TOPIC, groupId = "notification-service-rma-rejected")
    public void handleRmaRejected(String message) {
        process(message, RMA_REJECTED_TOPIC, CODE_REJECTED);
    }

    private void process(String message, String topic, String templateCode) {
        RmaEvent event;
        try {
            event = objectMapper.readValue(message, RmaEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize {} event", topic, e);
            return;
        }

        if (event == null || event.getRmaNumber() == null
                || event.getOrderId() == null || event.getUserEmail() == null) {
            log.warn("Skipping {} event with missing rmaNumber/orderId/userEmail: rmaNumber={}",
                    templateCode, event != null ? event.getRmaNumber() : null);
            return;
        }

        if (!deduplicator.claim(templateCode + ":" + event.getRmaNumber(), topic)) {
            log.warn("Duplicate {} event for rmaNumber={}, skipping", templateCode, event.getRmaNumber());
            return;
        }

        try {
            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    templateCode,
                    buildVariables(event),
                    event.getRmaNumber(),
                    ENTITY_TYPE);
            log.info("{} notification triggered for RMA {}", templateCode, event.getRmaNumber());
        } catch (Exception e) {
            log.error("Failed to dispatch {} notification for RMA {}",
                    templateCode, event.getRmaNumber(), e);
        }
    }

    private Map<String, Object> buildVariables(RmaEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("rmaNumber", event.getRmaNumber());
        vars.put("orderNumber", event.getOrderNumber() != null ? event.getOrderNumber() : event.getOrderId());
        vars.put("orderId", event.getOrderId());
        vars.put("returnLabelUrl", event.getReturnLabelUrl());
        vars.put("reason", event.getReason());
        vars.put("notes", event.getNotes());
        vars.put("occurredAt", event.getOccurredAt());
        return vars;
    }
}
