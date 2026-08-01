package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side mirror of the promotion-service announcement payload.
 *
 * <p>Tolerates unknown fields so producer-side schema additions do not break
 * deserialization here.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionEvent {
    private String eventType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime timestamp;

    private String promoCode;
    private String name;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime expiresAt;

    private String userId;
    private String userEmail;
    private String userName;
}
