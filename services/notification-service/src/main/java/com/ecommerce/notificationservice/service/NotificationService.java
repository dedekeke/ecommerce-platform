package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.domain.*;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Notification Service
 * Main service for sending notifications and managing notification logs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository logRepository;
    private final NotificationTemplateRepository templateRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    /**
     * Self-reference obtained through the Spring proxy. Retry sends must be
     * dispatched via this proxy so {@code @Async} actually takes effect — a plain
     * {@code this.sendNotification(...)} is a self-invocation that bypasses the
     * proxy and would run synchronously on the retry scheduler's single thread,
     * where one hung SMTP connection stalls the whole retry batch. {@code @Lazy}
     * breaks the constructor-time self-dependency cycle.
     */
    private NotificationService self;

    @Autowired
    public void setSelf(@Lazy NotificationService self) {
        this.self = self;
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * Send notification using template
     */
    @Async("notificationExecutor")
    public void sendNotification(
            String userId,
            String recipient,
            String templateCode,
            Map<String, Object> variables,
            String relatedEntityId,
            String relatedEntityType
    ) {
        log.info("Sending notification - userId: {}, template: {}, recipient: {}",
                userId, templateCode, recipient);

        // Get template
        NotificationTemplate template = templateRepository.findByCode(templateCode)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateCode));

        if (!template.getActive()) {
            log.warn("Template {} is not active, skipping notification", templateCode);
            return;
        }

        // Create notification log
        NotificationLog notificationLog = NotificationLog.builder()
                .userId(userId)
                .recipient(recipient)
                .type(template.getType())
                .templateCode(templateCode)
                .subject(template.getSubject())
                .variables(variables)
                .status(NotificationStatus.PENDING)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .retryCount(0)
                .build();

        notificationLog = logRepository.save(notificationLog);

        // Send based on type
        try {
            if (template.getType() == NotificationType.EMAIL) {
                emailService.sendEmail(recipient, template.getSubject(), template.getBody(), variables);
            } else if (template.getType() == NotificationType.SMS) {
                String message = processTemplate(template.getBody(), variables);
                smsService.sendSms(recipient, message);
            }

            // Update status to SENT
            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(Instant.now());
            logRepository.save(notificationLog);

            log.info("Notification sent successfully - id: {}", notificationLog.getId());

        } catch (Exception e) {
            log.error("Failed to send notification - id: {}", notificationLog.getId(), e);

            // Update status to FAILED
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(e.getMessage());

            // Schedule retry if under max attempts
            if (notificationLog.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                notificationLog.setStatus(NotificationStatus.RETRYING);
                notificationLog.setNextRetryAt(calculateNextRetryTime(notificationLog.getRetryCount()));
            }

            logRepository.save(notificationLog);
        }
    }

    /**
     * Retry failed notification
     */
    public void retryNotification(String notificationId) {
        NotificationLog notification = logRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        if (notification.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            throw new RuntimeException("Max retry attempts reached");
        }

        log.info("Retrying notification - id: {}, attempt: {}",
                notificationId, notification.getRetryCount() + 1);

        // Increment retry count
        notification.setRetryCount(notification.getRetryCount() + 1);
        notification.setStatus(NotificationStatus.RETRYING);
        logRepository.save(notification);

        // Re-send through the proxy so the @Async dispatch actually happens off
        // the retry scheduler thread.
        self.sendNotification(
                notification.getUserId(),
                notification.getRecipient(),
                notification.getTemplateCode(),
                notification.getVariables(),
                notification.getRelatedEntityId(),
                notification.getRelatedEntityType()
        );
    }

    /**
     * Get notification history
     */
    public Page<NotificationLog> getNotificationHistory(Pageable pageable) {
        return logRepository.findAll(pageable);
    }

    /**
     * Get user notifications
     */
    public Page<NotificationLog> getUserNotifications(String userId, Pageable pageable) {
        return logRepository.findByUserId(userId, pageable);
    }

    /**
     * Get notification by ID
     */
    public NotificationLog getNotification(String id) {
        return logRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
    }

    /**
     * Calculate next retry time with exponential backoff
     */
    private Instant calculateNextRetryTime(int retryCount) {
        long delayMinutes = (long) Math.pow(2, retryCount); // 1, 2, 4, 8 minutes
        return Instant.now().plus(delayMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Simple template processing for SMS
     */
    private String processTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }
}
