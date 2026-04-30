package com.ecommerce.orderservice.saga.refund;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload for the {@code refund.completed} Kafka topic. Consumed by
 * notification-service to send the refund-confirmation email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
