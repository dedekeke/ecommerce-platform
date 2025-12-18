package com.ecommerce.notificationservice.controller;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import com.ecommerce.notificationservice.repository.NotificationLogRepository;
import com.ecommerce.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Notification Controller
 * REST API for notification history and management
 */
@RestController
@RequestMapping("/api/notifications")
@Slf4j
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService notificationService;

    /**
     * Get all notifications with pagination
     */
    @GetMapping
    public ResponseEntity<Page<NotificationLog>> getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching all notifications - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationLog> notifications = notificationLogRepository.findAll(pageable);

        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<NotificationLog> getNotificationById(@PathVariable String id) {
        log.info("Fetching notification by id: {}", id);

        return notificationLogRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<NotificationLog>> getNotificationsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching notifications for user: {}", userId);
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationLog> notifications = notificationLogRepository.findByUserId(userId, pageable);

        return ResponseEntity.ok(notifications.getContent());
    }

    /**
     * Get failed notifications
     */
    @GetMapping("/failed")
    public ResponseEntity<List<NotificationLog>> getFailedNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Fetching failed notifications");
        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationLog> failedNotifications = notificationLogRepository.findByStatus(NotificationStatus.FAILED, pageable);

        return ResponseEntity.ok(failedNotifications.getContent());
    }

    /**
     * Retry a failed notification
     */
    @PostMapping("/retry/{id}")
    public ResponseEntity<String> retryNotification(@PathVariable String id) {
        log.info("Retrying notification: {}", id);

        return notificationLogRepository.findById(id)
                .map(notification -> {
                    if (NotificationStatus.FAILED.equals(notification.getStatus())
                            || NotificationStatus.RETRYING.equals(notification.getStatus())) {
                        try {
                            notificationService.retryNotification(id);
                            return ResponseEntity.ok("Notification retry initiated");
                        } catch (Exception e) {
                            return ResponseEntity.badRequest()
                                    .body("Failed to retry notification: " + e.getMessage());
                        }
                    } else {
                        return ResponseEntity.badRequest()
                                .body("Notification is not in FAILED or RETRYING status");
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
