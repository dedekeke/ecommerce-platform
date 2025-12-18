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
}
