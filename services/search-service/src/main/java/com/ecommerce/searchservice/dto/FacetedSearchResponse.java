package com.ecommerce.searchservice.dto;

import com.ecommerce.searchservice.document.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response shape for {@code GET /api/search} (§3.12).
 *
 * <p>Maps cleanly to the brief's example JSON: a list of products plus a
 * {@code totalElements} count and a {@code facets} map keyed by facet name
 * ({@code category}, {@code brand}, {@code priceRange}, {@code rating}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetedSearchResponse {

    private List<ProductDocument> products;
    private long totalElements;
    private Map<String, List<FacetBucket>> facets;
}
