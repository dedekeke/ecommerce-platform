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
    private final PushService pushService;

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

        dispatch(notificationLog, template);
    }

    /**
     * Re-send an existing notification row <em>in place</em>.
     *
     * <p>Reusing the same row — rather than creating a fresh {@link NotificationLog}
     * per retry — is what makes {@link #MAX_RETRY_ATTEMPTS} bound the TOTAL number
     * of attempts across the whole retry chain. A new row per retry would reset
     * {@code retryCount} to 0 every time, so a permanently-failing notification
     * would fan out into ever more rows and retry unboundedly.</p>
     */
    @Async("notificationExecutor")
    public void resend(String notificationId) {
        NotificationLog notificationLog = logRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        NotificationTemplate template = templateRepository.findByCode(notificationLog.getTemplateCode())
                .orElseThrow(() -> new RuntimeException(
                        "Template not found: " + notificationLog.getTemplateCode()));

        if (!template.getActive()) {
            log.warn("Template {} is not active, skipping retry", notificationLog.getTemplateCode());
            return;
        }

        dispatch(notificationLog, template);
    }

    /**
     * Send the given log via its channel and persist the outcome. On failure the
     * row's own {@code retryCount} decides whether another retry is scheduled, so
     * the cap bounds attempts across the retry chain.
     */
    private void dispatch(NotificationLog notificationLog, NotificationTemplate template) {
        String recipient = notificationLog.getRecipient();
        Map<String, Object> variables = notificationLog.getVariables();

        try {
            if (template.getType() == NotificationType.EMAIL) {
                emailService.sendEmail(recipient, template.getSubject(), template.getBody(), variables);
            } else if (template.getType() == NotificationType.SMS) {
                String message = processTemplate(template.getBody(), variables);
                smsService.sendSms(recipient, message);
            } else if (template.getType() == NotificationType.PUSH) {
                String body = processTemplate(template.getBody(), variables);
                pushService.sendPush(recipient, template.getSubject(), body, variables);
            }

            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setSentAt(Instant.now());
            notificationLog.setErrorMessage(null);
            logRepository.save(notificationLog);

            log.info("Notification sent successfully - id: {}", notificationLog.getId());

        } catch (Exception e) {
            log.error("Failed to send notification - id: {}", notificationLog.getId(), e);

            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(e.getMessage());
            // Clear any retry time carried over from a prior attempt; only re-set
            // it when we actually schedule another retry, so a terminally-failed
            // row never looks retry-eligible.
            notificationLog.setNextRetryAt(null);

            // Schedule another retry only while total attempts are under the cap.
            if (notificationLog.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                notificationLog.setStatus(NotificationStatus.RETRYING);
                notificationLog.setNextRetryAt(calculateNextRetryTime(notificationLog.getRetryCount()));
            }

            logRepository.save(notificationLog);
        }
    }

    /**
     * Retry a failed notification by re-sending the same row, carrying its
     * {@code retryCount} forward so {@link #MAX_RETRY_ATTEMPTS} caps total attempts.
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

        // Re-send the SAME row through the proxy so the @Async dispatch actually
        // happens off the retry scheduler thread and the retryCount is preserved.
        self.resend(notificationId);
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
