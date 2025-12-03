package com.ecommerce.paymentservice.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {
    private String eventType; // PAYMENT_COMPLETED, PAYMENT_FAILED
    private Long paymentId;
    private String orderId;
    private String userId;
    private String paymentIntentId;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
    private LocalDateTime timestamp;
}
