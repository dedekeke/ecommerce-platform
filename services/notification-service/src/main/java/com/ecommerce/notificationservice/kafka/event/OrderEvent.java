package com.ecommerce.notificationservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String orderId;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private String userName;
    private BigDecimal totalAmount;
    private String status;
    private String shippingAddress;

    /**
     * Optional recipient phone (E.164) — when present, an SMS is sent in addition to email.
     */
    private String userPhone;

    /**
     * Optional device registration token — when present, a push notification is sent too.
     */
    private String userDeviceToken;
}
