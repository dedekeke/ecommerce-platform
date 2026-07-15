package com.ecommerce.searchservice.repository;

import com.ecommerce.searchservice.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Search Repository
 * Elasticsearch repository for product search operations
 */
@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    List<ProductDocument> findByNameContainingIgnoreCase(String name);
    
    Page<ProductDocument> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String name, String description, Pageable pageable);
    
    Page<ProductDocument> findByCategory(String category, Pageable pageable);
    
    Page<ProductDocument> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    
    Page<ProductDocument> findByActiveTrue(Pageable pageable);
    
    /**
     * Autocomplete lookup capped at the query/ES level (size=10) via the
     * {@code Top10} keyword, so the shard never materialises an unbounded hit
     * set for a broad prefix. The legacy service-side {@code .limit(10)} became
     * redundant once the cap moved down here.
     */
    List<ProductDocument> findTop10ByNameAutocompleteContaining(String prefix);
}
