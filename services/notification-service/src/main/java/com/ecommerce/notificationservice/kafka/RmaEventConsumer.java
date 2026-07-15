package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
import com.ecommerce.notificationservice.kafka.dedup.NotificationEventDeduplicator;
import com.ecommerce.notificationservice.kafka.event.RmaEvent;
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
 * Consumes the three customer-facing RMA topics and emails the customer
 * via the existing template engine. {@code rma.received} is intentionally
 * not handled here — it is an internal warehouse event with no customer
 * email.
 *
 * <p>Idempotency is enforced insert-first via
 * {@link NotificationEventDeduplicator}: the {@code <templateCode>:<rmaNumber>}
 * dedup key is claimed on the unique {@code _id} index before any email is sent.
 * We key on {@code rmaNumber} (falling back to {@code orderId}) rather than
 * {@code orderId} alone because a single order can — in theory — have multiple
 * sequential returns over time. This replaces the previous check-then-insert
 * against the notification log, which raced under concurrency.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RmaEventConsumer {

    public static final String RMA_REQUESTED_TOPIC = "rma.requested";
    public static final String RMA_COMPLETED_TOPIC = "rma.completed";
    public static final String RMA_REJECTED_TOPIC = "rma.rejected";

    public static final String TEMPLATE_REQUESTED = "rma-requested";
    public static final String TEMPLATE_COMPLETED = "rma-completed";
    public static final String TEMPLATE_REJECTED = "rma-rejected";

    public static final String CODE_REQUESTED = "RMA_REQUESTED";
    public static final String CODE_COMPLETED = "RMA_COMPLETED";
    public static final String CODE_REJECTED = "RMA_REJECTED";

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationEventDeduplicator deduplicator;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = RMA_REQUESTED_TOPIC, groupId = "notification-service-rma-requested")
    public void handleRmaRequested(String message) {
        process(message, RMA_REQUESTED_TOPIC, CODE_REQUESTED, TEMPLATE_REQUESTED,
                "Your return has been authorized");
    }

    @KafkaListener(topics = RMA_COMPLETED_TOPIC, groupId = "notification-service-rma-completed")
    public void handleRmaCompleted(String message) {
        process(message, RMA_COMPLETED_TOPIC, CODE_COMPLETED, TEMPLATE_COMPLETED,
                "Your return has been completed");
    }

    @KafkaListener(topics = RMA_REJECTED_TOPIC, groupId = "notification-service-rma-rejected")
    public void handleRmaRejected(String message) {
        process(message, RMA_REJECTED_TOPIC, CODE_REJECTED, TEMPLATE_REJECTED,
                "Your return has been rejected");
    }

    private void process(String message, String topic, String templateCode,
                         String templateName, String subject) {
        RmaEvent event;
        try {
            event = objectMapper.readValue(message, RmaEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize {} event", topic, e);
            return;
        }

        if (event == null || event.getOrderId() == null || event.getUserEmail() == null) {
            log.warn("Skipping {} event with missing orderId or userEmail: rmaNumber={}",
                    templateCode, event != null ? event.getRmaNumber() : null);
            return;
        }

        String entityId = event.getRmaNumber() != null ? event.getRmaNumber() : event.getOrderId();
        if (!deduplicator.claim(templateCode + ":" + entityId, topic)) {
            log.warn("Duplicate {} event for {}, skipping", templateCode, entityId);
            return;
        }

        Map<String, Object> variables = buildVariables(event);

        NotificationLog logEntry = NotificationLog.builder()
                .userId(event.getUserId())
                .recipient(event.getUserEmail())
                .type(NotificationType.EMAIL)
                .templateCode(templateCode)
                .subject(subject)
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .relatedEntityId(entityId)
                .relatedEntityType("RMA")
                .retryCount(0)
                .build();
        logEntry = notificationLogRepository.save(logEntry);

        try {
            emailService.sendEmail(event.getUserEmail(), subject, templateName, variables);
            logEntry.setStatus(NotificationStatus.SENT);
            logEntry.setSentAt(Instant.now());
            notificationLogRepository.save(logEntry);
            log.info("{} email sent to {} for RMA {}", templateCode,
                    event.getUserEmail(), event.getRmaNumber());
        } catch (RuntimeException sendError) {
            log.error("Failed to send {} email for RMA {}", templateCode,
                    event.getRmaNumber(), sendError);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(sendError.getMessage());
            notificationLogRepository.save(logEntry);
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
