package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RefundCompletedEvent;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for {@code refund.completed} events published by the order-service
 * refund saga. Sends the customer a confirmation email and records the
 * delivery in the notification log.
 *
 * <p>Idempotency is enforced insert-first via
 * {@link NotificationEventDeduplicator}: the {@code REFUND_COMPLETED:<orderId>}
 * dedup key is claimed on the unique {@code _id} index before any email is sent,
 * so a concurrent replica or a redelivery cannot double-refund-email a customer.
 * This replaces the previous check-then-insert against the notification log.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundEventConsumer {

    public static final String REFUND_COMPLETED_TOPIC = "refund.completed";
    public static final String TEMPLATE_CODE = "REFUND_COMPLETED";
    public static final String TEMPLATE_NAME = "refund-completed";

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationEventDeduplicator deduplicator;
    private final EmailService emailService;
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

        String subject = "Your refund has been processed";
        Map<String, Object> variables = buildVariables(event);

        NotificationLog logEntry = NotificationLog.builder()
                .userId(event.getUserId())
                .recipient(event.getUserEmail())
                .type(NotificationType.EMAIL)
                .templateCode(TEMPLATE_CODE)
                .subject(subject)
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .relatedEntityId(event.getOrderId())
                .relatedEntityType("REFUND")
                .retryCount(0)
                .build();
        logEntry = notificationLogRepository.save(logEntry);

        try {
            emailService.sendEmail(event.getUserEmail(), subject, TEMPLATE_NAME, variables);
            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setSentAt(Instant.now());
            notificationLogRepository.save(logEntry);
            log.info("Refund-completed email sent to {} for order {}",
                    event.getUserEmail(), event.getOrderId());
        } catch (RuntimeException sendError) {
            log.error("Failed to send refund-completed email for order {}",
                    event.getOrderId(), sendError);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(sendError.getMessage());
            notificationLogRepository.save(logEntry);
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
