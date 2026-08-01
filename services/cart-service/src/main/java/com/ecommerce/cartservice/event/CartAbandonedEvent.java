package com.ecommerce.cartservice.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Event published when an abandoned cart is detected (§3.10).
 *
 * <p>Topic: {@code cart.abandoned}. The {@code cartId} is used as the Kafka
 * partition key so all events for the same cart land in the same partition,
 * which makes downstream idempotency cheaper.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartAbandonedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TOPIC = "cart.abandoned";

    private String cartId;
    private String userId;
    private String userEmail;
    private String userName;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private List<LineItem> lineItems;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant abandonedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String productId;
        private String productName;
        private String productImageUrl;
        private BigDecimal price;
        private Integer quantity;
    }
}
