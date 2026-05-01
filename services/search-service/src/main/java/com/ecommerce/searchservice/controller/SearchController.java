package com.ecommerce.searchservice.controller;

import com.ecommerce.searchservice.dto.AutocompleteResponse;
import com.ecommerce.searchservice.dto.FacetedSearchRequest;
import com.ecommerce.searchservice.dto.FacetedSearchResponse;
import com.ecommerce.searchservice.dto.ProductSearchRequest;
import com.ecommerce.searchservice.dto.ProductSearchResponse;
import com.ecommerce.searchservice.dto.SuggestResponse;
import com.ecommerce.searchservice.service.FacetedSearchService;
import com.ecommerce.searchservice.service.ProductSearchService;
import com.ecommerce.searchservice.service.SuggestService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Search Controller
 * REST API for product search operations
 */
@RestController
@RequestMapping("/api/search")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Search", description = "Product search API")
public class SearchController {

    private final ProductSearchService searchService;
    private final SuggestService suggestService;
    private final FacetedSearchService facetedSearchService;

    @PostMapping("/products")
    @Operation(summary = "Search products", description = "Search products with filters and facets")
    public ResponseEntity<ProductSearchResponse> searchProducts(
            @RequestBody ProductSearchRequest request) {
        log.info("Search request received: {}", request);
        ProductSearchResponse response = searchService.searchProducts(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Autocomplete", description = "Get autocomplete suggestions")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @RequestParam String query) {
        log.info("Autocomplete request for: {}", query);
        AutocompleteResponse response = searchService.autocomplete(query);
        return ResponseEntity.ok(response);
    }

    /**
     * Faceted search (§3.12). Returns matching products plus
     * {@code category}, {@code brand}, {@code priceRange} and {@code rating}
     * facets. Filters narrow the result set <em>and</em> the facet counts.
     *
     * <p>Example: {@code GET /api/search?q=laptop&categories=Electronics,Books
     * &minPrice=100&maxPrice=500&minRating=4&facets=category,brand,priceRange,rating}
     */
    @GetMapping
    @Operation(summary = "Faceted search",
            description = "Search with category/brand/price/rating filters and aggregations.")
    public ResponseEntity<FacetedSearchResponse> facetedSearch(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categories", required = false) String categoriesCsv,
            @RequestParam(value = "brands", required = false) String brandsCsv,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "minRating", required = false) Double minRating,
            @RequestParam(value = "facets", required = false) String facetsCsv,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        FacetedSearchRequest request = FacetedSearchRequest.builder()
                .query(q)
                .categories(splitCsv(categoriesCsv))
                .brands(splitCsv(brandsCsv))
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .facets(splitCsv(facetsCsv))
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(facetedSearchService.search(request));
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @GetMapping("/suggest")
    @Operation(summary = "Search-as-you-type",
            description = "Returns product suggestions for a query prefix, ranked by Elasticsearch's search_as_you_type field. Cached 30s in Redis.")
    public ResponseEntity<List<SuggestResponse>> suggest(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "8") int limit) {
        log.debug("Suggest request q='{}' limit={}", q, limit);
        return ResponseEntity.ok(suggestService.suggest(q, limit));
    }
}
