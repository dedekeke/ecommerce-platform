package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side payload mirror for the four RMA topics
 * ({@code rma.requested}, {@code rma.received}, {@code rma.completed},
 * {@code rma.rejected}) published by the order-service RMA saga.
 *
 * <p>Tolerates unknown fields so producer additions do not break us.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
