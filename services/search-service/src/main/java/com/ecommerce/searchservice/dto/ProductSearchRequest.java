package com.ecommerce.searchservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product search request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {
    
    private String query;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> tags;
    private Boolean activeOnly;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
