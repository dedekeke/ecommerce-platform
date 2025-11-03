package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Event published when a product is updated.
 * Triggers search index update and cache invalidation.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductUpdatedEvent extends BaseEvent {

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
     * Product tags
     */
    private List<String> tags;

    /**
     * Fields that were changed (for partial updates)
     */
    private List<String> changedFields;

    /**
     * Previous values of changed fields
     */
    private Map<String, Object> previousValues;

    @Override
    public String getPartitionKey() {
        return productId;
    }
}
