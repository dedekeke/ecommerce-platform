package com.ecommerce.orderservice.saga.rma;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single payload class shared across all RMA Kafka topics
 * ({@code rma.requested}, {@code rma.received}, {@code rma.completed},
 * {@code rma.rejected}). Consumers branch on the topic, not on a type
 * field — keeping one envelope avoids polymorphic deserialization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmaEvent {

    private String rmaId;
    private String rmaNumber;
    private String orderId;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private String returnLabelUrl;
    private String outcome;
    private String reason;
    private String notes;
    private LocalDateTime occurredAt;
}
