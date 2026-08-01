package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound request for the {@code GET /api/search} faceted endpoint (§3.12).
 *
 * <p>Built from query string parameters by {@link com.ecommerce.searchservice.controller.SearchController}.
 * Filters are AND-combined and applied via a {@code bool.filter} clause so
 * the facet counts always reflect the <em>currently filtered</em> result set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetedSearchRequest {

    /** Free-text query (matched against name + description). */
    private String query;

    /** Filter: keep only documents in any of these categories. */
    private List<String> categories;

    /** Filter: keep only documents in any of these brands. */
    private List<String> brands;

    /** Inclusive lower price bound. */
    private BigDecimal minPrice;

    /** Inclusive upper price bound. */
    private BigDecimal maxPrice;

    /** Inclusive lower rating bound. */
    private Double minRating;

    /** Which facets to compute. Empty/null → all four. */
    private List<String> facets;

    private Integer page;
    private Integer size;
}
