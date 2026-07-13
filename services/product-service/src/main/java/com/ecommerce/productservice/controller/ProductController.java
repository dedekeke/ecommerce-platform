package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.PageResponse;
import com.ecommerce.productservice.dto.ProductRequest;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Set;

/**
 * REST controller for Product management.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;
    private final CategoryService categoryService;

    /**
     * Allowlist of entity property names permitted as sort fields.
     * Prevents JPA property-path injection where an attacker could supply arbitrary
     * property paths (e.g. "category.name") to probe schema structure.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "price", "createdAt", "updatedAt", "stockQuantity", "sku"
    );
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    @GetMapping
    @Operation(summary = "Get all products", description = "Get all products with pagination, search, and filters")
    public ResponseEntity<PageResponse<ProductResponse>> getAllProducts(
            @Parameter(description = "Search term for name/description")
            @RequestParam(required = false) String search,

            @Parameter(description = "Category ID or slug filter")
            @RequestParam(required = false) String categoryId,

            @Parameter(description = "Minimum price filter")
            @RequestParam(required = false) BigDecimal minPrice,

            @Parameter(description = "Maximum price filter")
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Filter active products only")
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,

            @Parameter(description = "Filter in-stock products only")
            @RequestParam(required = false, defaultValue = "false") boolean inStockOnly,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction (asc/desc)")
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        log.info("GET /api/products - search: {}, categoryId: {}, priceRange: {}-{}, activeOnly: {}, inStockOnly: {}",
            search, categoryId, minPrice, maxPrice, activeOnly, inStockOnly);

        // Resolve categoryId - can be numeric ID or slug
        Long resolvedCategoryId = resolveCategoryId(categoryId);

        // SECURITY: Validate sortBy against an allowlist to prevent JPA property-path injection.
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : DEFAULT_SORT_FIELD;
        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, safeSortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> products;

        if (search != null || resolvedCategoryId != null || minPrice != null || maxPrice != null) {
            // Advanced search
            products = productService.advancedSearch(search, resolvedCategoryId, minPrice, maxPrice,
                activeOnly, inStockOnly, pageable);
        } else if (activeOnly && inStockOnly) {
            products = productService.getAvailableProducts(pageable);
        } else if (activeOnly) {
            products = productService.getActiveProducts(pageable);
        } else {
            products = productService.getAllProducts(pageable);
        }

        Page<ProductResponse> response = products.map(productMapper::toResponse);
        return ResponseEntity.ok(PageResponse.from(response));
    }

    private Long resolveCategoryId(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(categoryId);
        } catch (NumberFormatException e) {
            // Not a number, try to resolve as slug
            return categoryService.findCategoryBySlug(categoryId)
                .map(category -> category.getId())
                .orElse(null);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "Product ID")
            @PathVariable Long id
    ) {
        log.info("GET /api/v1/products/{}", id);

        Product product = productService.getProductById(id);
        ProductResponse response = productMapper.toResponse(product);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<ProductResponse> getProductBySku(
            @Parameter(description = "Product SKU")
            @PathVariable String sku
    ) {
        log.info("GET /api/v1/products/sku/{}", sku);

        Product product = productService.getProductBySku(sku);
        ProductResponse response = productMapper.toResponse(product);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products")
    public ResponseEntity<PageResponse<ProductResponse>> getFeaturedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("GET /api/v1/products/featured");

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getFeaturedProducts(pageable);
        Page<ProductResponse> response = products.map(productMapper::toResponse);
        return ResponseEntity.ok(PageResponse.from(response));
    }

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request
    ) {
        log.info("POST /api/v1/products - SKU: {}", request.getSku());

        Product product = productMapper.toEntity(request);
        Product created = productService.createProduct(product);
        ProductResponse response = productMapper.toResponse(created);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ProductResponse> updateProduct(
            @Parameter(description = "Product ID")
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        log.info("PUT /api/v1/products/{}", id);

        Product product = productMapper.toEntity(request);
        Product updated = productService.updateProduct(id, product);
        ProductResponse response = productMapper.toResponse(updated);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/stock")
    @Operation(summary = "Update product stock quantity")
    public ResponseEntity<ProductResponse> updateStock(
            @Parameter(description = "Product ID")
            @PathVariable Long id,
            @Parameter(description = "New stock quantity")
            @RequestParam int quantity
    ) {
        log.info("PATCH /api/v1/products/{}/stock - quantity: {}", id, quantity);

        Product updated = productService.updateStockQuantity(id, quantity);
        ProductResponse response = productMapper.toResponse(updated);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product (soft delete)")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Product ID")
            @PathVariable Long id
    ) {
        log.info("DELETE /api/v1/products/{}", id);

        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
