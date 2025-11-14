package com.ecommerce.productservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when a product is created.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreatedEvent {

    private Long productId;
    private String sku;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private String currency;
    private Integer stockQuantity;
    private Boolean active;
    private LocalDateTime createdAt;
}
