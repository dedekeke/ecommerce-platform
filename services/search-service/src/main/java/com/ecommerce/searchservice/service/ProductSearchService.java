package com.ecommerce.searchservice.service;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.AutocompleteResponse;
import com.ecommerce.searchservice.dto.ProductSearchRequest;
import com.ecommerce.searchservice.dto.ProductSearchResponse;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Product Search Service
 * Implements full-text search, filtering, faceting, and autocomplete
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductSearchRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Search products with filters and facets
     */
    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        log.info("Searching products with request: {}", request);

        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        
        Pageable pageable = PageRequest.of(page, size, getSort(request));
        
        Page<ProductDocument> results;
        
        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            results = repository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                    request.getQuery(), request.getQuery(), pageable);
        } else {
            results = repository.findByActiveTrue(pageable);
        }

        // Get facets (aggregations)
        Map<String, Long> categoryFacets = getCategoryFacets();
        Map<String, Long> tagFacets = getTagFacets();

        return ProductSearchResponse.builder()
                .products(results.getContent())
                .totalHits(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .currentPage(page)
                .categoryFacets(categoryFacets)
                .tagFacets(tagFacets)
                .build();
    }

    /**
     * Autocomplete suggestions
     */
    public AutocompleteResponse autocomplete(String prefix) {
        log.info("Getting autocomplete suggestions for: {}", prefix);
        
        List<ProductDocument> products = repository.findByNameAutocompleteContaining(prefix);
        List<String> suggestions = products.stream()
                .map(ProductDocument::getName)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return AutocompleteResponse.builder()
                .suggestions(suggestions)
                .build();
    }

    /**
     * Index a product
     */
    public ProductDocument indexProduct(ProductDocument product) {
        log.info("Indexing product: {}", product.getId());
        product.setNameAutocomplete(product.getName());
        return repository.save(product);
    }

    /**
     * Delete a product from index
     */
    public void deleteProduct(String productId) {
        log.info("Deleting product from index: {}", productId);
        repository.deleteById(productId);
    }

    /**
     * Get product by ID
     */
    public Optional<ProductDocument> getProduct(String id) {
        return repository.findById(id);
    }

    private Sort getSort(ProductSearchRequest request) {
        if (request.getSortBy() == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Sort.Direction direction = "DESC".equalsIgnoreCase(request.getSortDirection()) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;

        return Sort.by(direction, request.getSortBy());
    }

    private Map<String, Long> getCategoryFacets() {
        // Simplified facet implementation
        // In production, use Elasticsearch aggregations
        return new HashMap<>();
    }

    private Map<String, Long> getTagFacets() {
        // Simplified facet implementation  
        // In production, use Elasticsearch aggregations
        return new HashMap<>();
    }
}
