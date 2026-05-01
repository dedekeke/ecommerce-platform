package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single bucket within a facet (e.g. {@code {value: "Electronics", count: 25}}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetBucket {
    private String value;
    private long count;
}
