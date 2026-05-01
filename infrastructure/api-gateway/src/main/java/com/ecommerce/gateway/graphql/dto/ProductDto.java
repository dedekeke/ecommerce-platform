package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Minimal product projection consumed by the BFF.
 *
 * <p>Mirrors only the subset of {@code product-service}'s {@code ProductResponse}
 * needed to populate the GraphQL {@code Product} type. We deliberately do not
 * share the backend DTO class — the BFF keeps a thin contract so that
 * non-additive changes in product-service don't break the GraphQL boundary.
 *
 * <p>Fields that the schema may ask for but the upstream service does not
 * always populate (description, images, category) are nullable by intent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        String id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        List<String> images,
        CategoryDto category,
        Integer stockQuantity,
        Boolean inStock
) {
    public boolean resolvedInStock() {
        if (inStock != null) {
            return inStock;
        }
        return stockQuantity != null && stockQuantity > 0;
    }
}
