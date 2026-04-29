package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
