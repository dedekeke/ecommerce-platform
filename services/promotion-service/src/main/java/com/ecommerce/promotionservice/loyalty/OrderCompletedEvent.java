package com.ecommerce.promotionservice.loyalty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Local mirror of the order-service {@code OrderEvent} payload — enough
 * fields for the loyalty consumer; the rest are ignored. Schema
 * additions on the producer side are tolerated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCompletedEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;

    private String orderId;
    private String orderNumber;
    private String userId;

    private BigDecimal total;
    private BigDecimal totalAmount;
}
