package com.ecommerce.promotionservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Promotion lifecycle event published to Kafka.
 * Consumed by notification-service to send promotion announcement emails.
 *
 * `userEmail` / `userName` are optional — when absent the consumer falls back
 * to a configured admin/announcement recipient.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionEvent {
    private String eventType;
    private LocalDateTime timestamp;
    private String promoCode;
    private String name;
    private String description;
    private LocalDateTime expiresAt;
    private String userId;
    private String userEmail;
    private String userName;
}
