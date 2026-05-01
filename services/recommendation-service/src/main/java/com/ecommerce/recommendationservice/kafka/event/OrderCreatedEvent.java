package com.ecommerce.recommendationservice.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Locally defined event shape consumed from the {@code order.created} Kafka topic.
 *
 * <p>The platform's authoritative event is
 * {@link com.ecommerce.common.event.OrderCreatedEvent}, but it carries many fields
 * we do not need (addresses, taxes, payment method) and pulls a fatter object
 * graph through the deserialiser. Recommendation-service only needs the order
 * key, the user, and the list of productIds — so we deserialise into this slim
 * shape with {@code @JsonIgnoreProperties} semantics from Jackson defaults
 * (unknown properties are ignored via the {@code FAIL_ON_UNKNOWN_PROPERTIES=false}
 * setting in {@code application.yml}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCreatedEvent {

    private String orderId;

    private String userId;

    private List<OrderItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItem {
        private String productId;
        private Integer qty;
    }
}
