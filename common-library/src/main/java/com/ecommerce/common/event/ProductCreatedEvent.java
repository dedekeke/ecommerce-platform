package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Event published when a new product is created.
 * Triggers search index update and cache invalidation.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductCreatedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * Product identifier
     */
    private String productId;

    /**
     * Stock Keeping Unit
     */
    private String sku;

    /**
     * Product name
     */
    private String name;

    /**
     * Product description
     */
    private String description;

    /**
     * Category identifier
     */
    private String categoryId;

    /**
     * Category name
     */
    private String categoryName;

    /**
     * Price
     */
    private BigDecimal price;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Product images
     */
    private List<String> images;

    /**
     * Stock quantity
     */
    private Integer stockQuantity;

    /**
     * Whether product is active/visible
     */
    private Boolean active;

    /**
     * Product tags for search
     */
    private List<String> tags;

    @Override
    public String getPartitionKey() {
        return productId;
    }
}
