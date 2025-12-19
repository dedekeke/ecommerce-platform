package com.ecommerce.searchservice.dto;

import com.ecommerce.searchservice.document.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Product search response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {
    
    private List<ProductDocument> products;
    private long totalHits;
    private int totalPages;
    private int currentPage;
    private Map<String, Long> categoryFacets;
    private Map<String, Long> tagFacets;
}
