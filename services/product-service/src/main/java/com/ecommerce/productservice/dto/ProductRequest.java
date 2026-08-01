package com.ecommerce.productservice.dto;

import com.ecommerce.productservice.model.Dimensions;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

/**
 * DTO for creating or updating a product.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 50, message = "SKU must not exceed 50 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must not exceed 255 characters")
    private String name;

    private String description;

    private Long categoryId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal price;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters")
    private String currency;

    private Set<String> images;

    private Dimensions dimensions;

    /**
     * CREATE-ONLY. Seeds the catalog stock snapshot on POST /api/products.
     * IGNORED on PUT /api/products/{id}: stock movement is owned by
     * inventory-service, and echoing a stale snapshot back through a general
     * update would clobber concurrent stock changes. Use
     * PATCH /api/products/{id}/stock?quantity=N to change it.
     */
    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Boolean active;
}
