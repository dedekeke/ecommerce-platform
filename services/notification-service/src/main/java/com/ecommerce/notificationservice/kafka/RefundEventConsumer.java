package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RefundCompletedEvent;
import com.ecommerce.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for {@code refund.completed} events published by the order-service
 * refund saga. Emails the customer a confirmation.
 *
 * <p><b>Idempotency.</b> Enforced insert-first via
 * {@link NotificationEventDeduplicator}: the {@code REFUND_COMPLETED:<orderId>}
 * key is claimed on the unique {@code _id} index before dispatch, so a
 * concurrent replica or Kafka redelivery cannot double-email a customer. The
 * claim is on the consumed <em>event</em> (fire-once); it is deliberately
 * permanent.
 *
 * <p><b>Delivery + retry.</b> The send is routed through
 * {@link NotificationService#sendNotification} — the same retry-capable path as
 * the order/cart consumers. On a transient email failure that method persists
 * the log as RETRYING with a {@code nextRetryAt}, and
 * {@code NotificationRetryScheduler} re-sends the same persisted row (not via
 * Kafka redelivery). Keeping a permanent dedup claim is therefore correct: the
 * event is consumed once, while delivery is retried internally.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundEventConsumer {

    public static final String REFUND_COMPLETED_TOPIC = "refund.completed";
    public static final String TEMPLATE_CODE = "REFUND_COMPLETED";
    public static final String ENTITY_TYPE = "REFUND";

    private final NotificationService notificationService;
    private final NotificationEventDeduplicator deduplicator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = REFUND_COMPLETED_TOPIC, groupId = "notification-service-refund")
    public void handleRefundCompleted(String message) {
        RefundCompletedEvent event;
        try {
            event = objectMapper.readValue(message, RefundCompletedEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize {} event", REFUND_COMPLETED_TOPIC, e);
            return;
        }

        if (event == null || event.getOrderId() == null || event.getUserEmail() == null) {
            log.warn("Skipping refund.completed event with missing orderId or userEmail: sagaId={}",
                    event != null ? event.getSagaId() : null);
            return;
        }

        if (!deduplicator.claim(TEMPLATE_CODE + ":" + event.getOrderId(), REFUND_COMPLETED_TOPIC)) {
            log.warn("Duplicate refund.completed event for orderId={}, skipping", event.getOrderId());
            return;
        }

        try {
            notificationService.sendNotification(
                    event.getUserId(),
                    event.getUserEmail(),
                    TEMPLATE_CODE,
                    buildVariables(event),
                    event.getOrderId(),
                    ENTITY_TYPE);
            log.info("Refund-completed notification triggered for order {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to dispatch refund-completed notification for order {}",
                    event.getOrderId(), e);
        }
    }

    private Map<String, Object> buildVariables(RefundCompletedEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderNumber", event.getOrderNumber() != null ? event.getOrderNumber() : event.getOrderId());
        vars.put("orderId", event.getOrderId());
        vars.put("amount", event.getAmount());
        vars.put("refundTransactionId", event.getRefundTransactionId());
        vars.put("completedAt", event.getCompletedAt());
        return vars;
    }
}
