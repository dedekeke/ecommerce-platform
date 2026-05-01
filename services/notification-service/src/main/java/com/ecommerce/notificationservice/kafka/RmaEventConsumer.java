package com.ecommerce.notificationservice.kafka;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.domain.NotificationType;
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
import java.util.List;
import java.util.Map;

/**
 * Consumes the three customer-facing RMA topics and emails the customer
 * via the existing template engine. {@code rma.received} is intentionally
 * not handled here — it is an internal warehouse event with no customer
 * email.
 *
 * <p>Idempotency: each topic uses a different {@code templateCode}, and
 * we de-dupe per ({@code orderId}, {@code templateCode}) so a producer
 * retry doesn't double-mail the customer.</p>
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

    private static final List<NotificationStatus> ACTIVE_STATUSES =
        List.of(NotificationStatus.SENT, NotificationStatus.PENDING, NotificationStatus.RETRYING);

    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = RMA_REQUESTED_TOPIC, groupId = "notification-service-rma-requested")
    public void handleRmaRequested(String message) {
        process(message, CODE_REQUESTED, TEMPLATE_REQUESTED, "Your return has been authorized");
    }

    @KafkaListener(topics = RMA_COMPLETED_TOPIC, groupId = "notification-service-rma-completed")
    public void handleRmaCompleted(String message) {
        process(message, CODE_COMPLETED, TEMPLATE_COMPLETED, "Your return has been completed");
    }

    @KafkaListener(topics = RMA_REJECTED_TOPIC, groupId = "notification-service-rma-rejected")
    public void handleRmaRejected(String message) {
        process(message, CODE_REJECTED, TEMPLATE_REJECTED, "Your return has been rejected");
    }

    private void process(String message, String templateCode, String templateName, String subject) {
        try {
            RmaEvent event = objectMapper.readValue(message, RmaEvent.class);

            if (event.getOrderId() == null || event.getUserEmail() == null) {
                log.warn("Skipping {} event with missing orderId or userEmail: rmaNumber={}",
                    templateCode, event.getRmaNumber());
                return;
            }

            // Per-RMA idempotency. We key on rmaNumber+templateCode rather
            // than orderId because a single order can — in theory — have
            // multiple sequential returns over time (one per item lifecycle).
            String dedupKey = event.getRmaNumber() != null ? event.getRmaNumber() : event.getOrderId();
            if (isDuplicate(dedupKey, templateCode)) {
                log.warn("Duplicate {} event for {}, skipping", templateCode, dedupKey);
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
                .relatedEntityId(dedupKey)
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

        } catch (Exception e) {
            log.error("Failed to process {} event", templateCode, e);
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

    private boolean isDuplicate(String dedupKey, String templateCode) {
        return notificationLogRepository.existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
            dedupKey, templateCode, ACTIVE_STATUSES);
    }
}
