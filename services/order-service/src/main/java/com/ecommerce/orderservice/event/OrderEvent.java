package com.ecommerce.orderservice.event;

import com.ecommerce.orderservice.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Base class for order-related events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime timestamp;

    // Order information
    private String orderId;
    private String orderNumber;
    private String userId;

    // Order details
    private List<OrderItemEvent> items;
    private BigDecimal total;
    private OrderStatus status;

    // Additional context
    private String paymentIntentId;
    private String reservationId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;
    }
}
