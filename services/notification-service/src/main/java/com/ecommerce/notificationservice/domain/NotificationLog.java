package com.ecommerce.notificationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Notification Log
 * Stores history of all sent notifications
 */
@Document(collection = "notification_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    private String id;

    /**
     * User ID receiving the notification
     */
    private String userId;

    /**
     * Recipient (email address or phone number)
     */
    private String recipient;

    /**
     * Notification type
     */
    private NotificationType type;

    /**
     * Template code used
     */
    private String templateCode;

    /**
     * Subject (for email)
     */
    private String subject;

    /**
     * Message body
     */
    private String body;

    /**
     * Variables used in template
     */
    private Map<String, Object> variables;

    /**
     * Notification status
     */
    private NotificationStatus status;

    /**
     * Error message if failed
     */
    private String errorMessage;

    /**
     * Number of retry attempts
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Next retry time
     */
    private Instant nextRetryAt;

    /**
     * Related entity ID (order ID, payment ID, etc.)
     */
    private String relatedEntityId;

    /**
     * Related entity type (ORDER, PAYMENT, SHIPMENT, etc.)
     */
    private String relatedEntityType;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Time when notification was sent
     */
    private Instant sentAt;
}
