package com.ecommerce.notificationservice.scheduler;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Notification Retry Scheduler
 * Periodically checks for failed notifications that need retry
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService notificationService;

    /**
     * Check for notifications to retry every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void retryFailedNotifications() {
        log.info("Checking for notifications to retry");

        List<NotificationLog> notificationsToRetry = notificationLogRepository
                .findByStatusAndNextRetryAtBefore(NotificationStatus.RETRYING, Instant.now());

        if (notificationsToRetry.isEmpty()) {
            log.debug("No notifications to retry");
            return;
        }

        log.info("Found {} notifications to retry", notificationsToRetry.size());

        for (NotificationLog notification : notificationsToRetry) {
            try {
                log.info("Retrying notification: {} (attempt: {})",
                        notification.getId(), notification.getRetryCount() + 1);

                notificationService.retryNotification(notification.getId());
            } catch (Exception e) {
                log.error("Failed to retry notification: {}", notification.getId(), e);
            }
        }
    }
}
