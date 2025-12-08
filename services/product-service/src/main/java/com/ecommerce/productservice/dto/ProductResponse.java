package com.ecommerce.productservice.dto;

import com.ecommerce.productservice.model.Dimensions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for product responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private String id;
    private String sku;
    private String name;
    private String description;
    private CategoryResponse category;
    private BigDecimal price;
    private String currency;
    private Set<String> images;
    private Dimensions dimensions;
    private Integer stockQuantity;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Computed fields
    private Boolean inStock;
    private Boolean available;
}
