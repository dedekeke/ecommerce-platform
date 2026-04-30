package com.ecommerce.searchservice.service;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.dto.AutocompleteResponse;
import com.ecommerce.searchservice.dto.ProductSearchRequest;
import com.ecommerce.searchservice.dto.ProductSearchResponse;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
     * Multiplier applied to documents flagged with {@code lowStockPenalty=true}
     * by the replenishment saga.
     */
    private static final double LOW_STOCK_PENALTY_FACTOR = 0.5;

    /**
     * Search products with filters and facets.
     *
     * <p>Applies a function-score query that multiplies the relevance of
     * documents with {@code lowStockPenalty=true} by {@link #LOW_STOCK_PENALTY_FACTOR}
     * so out-of-stock products rank lower without being excluded outright.
     */
    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        log.info("Searching products with request: {}", request);

        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;

        Pageable pageable = PageRequest.of(page, size, getSort(request));

        Page<ProductDocument> results = searchWithLowStockPenalty(request, pageable);

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

    /**
     * Build the inner relevance query (matches text against name/description
     * or active=true when no query) and wrap it in a function_score that
     * de-boosts documents with {@code lowStockPenalty=true}.
     */
    Page<ProductDocument> searchWithLowStockPenalty(ProductSearchRequest request, Pageable pageable) {
        Query innerQuery;
        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            String text = request.getQuery();
            innerQuery = Query.of(q -> q.bool(BoolQuery.of(b -> b
                    .should(s -> s.match(m -> m.field("name").query(text)))
                    .should(s -> s.match(m -> m.field("description").query(text)))
                    .minimumShouldMatch("1"))));
        } else {
            innerQuery = Query.of(q -> q.term(t -> t.field("active").value(true)));
        }

        FunctionScore penalty = FunctionScore.of(fs -> fs
                .filter(f -> f.term(t -> t.field("lowStockPenalty").value(true)))
                .weight(LOW_STOCK_PENALTY_FACTOR));

        Query scored = Query.of(q -> q.functionScore(FunctionScoreQuery.of(fsq -> fsq
                .query(innerQuery)
                .functions(penalty)
                .scoreMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode.Multiply)
                .boostMode(co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode.Multiply))));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(scored)
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(
                nativeQuery, ProductDocument.class, IndexCoordinates.of("products"));

        List<ProductDocument> content = hits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, hits.getTotalHits());
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
