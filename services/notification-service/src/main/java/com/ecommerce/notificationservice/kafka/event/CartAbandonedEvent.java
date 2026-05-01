package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Mirror of {@code com.ecommerce.cartservice.event.CartAbandonedEvent}, used
 * by the notification-service to deserialize {@code cart.abandoned} messages
 * without depending on the cart-service module.
 *
 * <p>Unknown properties are tolerated so producer-side schema additions don't
 * break the consumer.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartAbandonedEvent {

    private String cartId;
    private String userId;
    private String userEmail;
    private String userName;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private List<LineItem> lineItems;
    private Instant abandonedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineItem {
        private String productId;
        private String productName;
        private String productImageUrl;
        private BigDecimal price;
        private Integer quantity;
    }
}
