package com.ecommerce.searchservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Event from Kafka
 * Received when products are created or updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {
    private String id;
    private String name;
    private String description;
    private String sku;
    private String category;
    private BigDecimal price;
    private String currency;
    private List<String> images;
    private Boolean active;
    private Integer stockQuantity;
    private List<String> tags;
    private String eventType; // CREATED, UPDATED, DELETED
}
