package com.ecommerce.searchservice.controller;

import com.ecommerce.searchservice.dto.AutocompleteResponse;
import com.ecommerce.searchservice.dto.ProductSearchRequest;
import com.ecommerce.searchservice.dto.ProductSearchResponse;
import com.ecommerce.searchservice.dto.SuggestResponse;
import com.ecommerce.searchservice.service.ProductSearchService;
import com.ecommerce.searchservice.service.SuggestService;
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
