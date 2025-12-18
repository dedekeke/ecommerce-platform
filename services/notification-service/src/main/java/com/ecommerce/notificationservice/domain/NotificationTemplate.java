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
 * Notification Template
 * Stores reusable templates for notifications
 */
@Document(collection = "notification_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    private String id;

    /**
     * Unique template code (e.g., "ORDER_CONFIRMATION", "PAYMENT_RECEIPT")
     */
    private String code;

    /**
     * Template name
     */
    private String name;

    /**
     * Description of the template
     */
    private String description;

    /**
     * Notification type (EMAIL, SMS, PUSH)
     */
    private NotificationType type;

    /**
     * Subject line (for email)
     */
    private String subject;

    /**
     * Template body (Thymeleaf template for email, plain text for SMS)
     */
    private String body;

    /**
     * Default variables for the template
     */
    private Map<String, Object> defaultVariables;

    /**
     * Whether the template is active
     */
    private Boolean active;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
