package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consumer-side payload mirror for the {@code refund.completed} topic
 * published by the order-service refund saga.
 *
 * <p>Tolerates unknown fields so producer additions do not break consumers.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundCompletedEvent {

    private String sagaId;
    private String orderId;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private String refundTransactionId;
    private BigDecimal amount;
    private LocalDateTime completedAt;
}
