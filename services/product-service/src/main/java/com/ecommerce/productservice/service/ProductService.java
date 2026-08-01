package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service layer for Product management.
 * Optimized for read-heavy workload with caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final com.ecommerce.productservice.event.ProductEventPublisher eventPublisher;
    private final ProductListingCache listingCache;
    private final ProductMapper productMapper;

    /**
     * Get product by ID.
     *
     * sync = true uses Caffeine's get(key, loader) under the hood (via the
     * LayeredCacheManager) so that 100 concurrent threads missing the same id
     * inside one pod collapse to a SINGLE database fetch. This is the
     * application-layer single-flight protection against cache stampede on
     * hot product keys.
     */
    @Cacheable(value = "products", key = "'id:' + #id", sync = true)
    public Product getProductById(Long id) {
        log.debug("Fetching product by ID: {}", id);
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
    }

    /**
     * Get product by SKU.
     *
     * Same single-flight rationale as {@link #getProductById}.
     */
    @Cacheable(value = "products", key = "'sku:' + #sku", sync = true)
    public Product getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with SKU: " + sku));
    }

    /**
     * Get all products with pagination. Served from the short-TTL listing cache
     * as already-mapped DTOs (see {@link ProductListingCache}).
     */
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products with pagination: {}", pageable);
        return listingCache.getAllProducts(pageable).toPage(pageable);
    }

    /**
     * Get active products only.
     */
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        log.debug("Fetching active products with pagination: {}", pageable);
        return listingCache.getActiveProducts(pageable).toPage(pageable);
    }

    /**
     * Get available products (active and in stock). Uncached; mapped within this
     * read-only transaction so the returned DTOs are session-independent.
     */
    public Page<ProductResponse> getAvailableProducts(Pageable pageable) {
        log.debug("Fetching available products with pagination: {}", pageable);
        return productRepository.findAvailable(pageable).map(productMapper::toResponse);
    }

    /**
     * Search products by name or description.
     */
    public Page<Product> searchProducts(String searchTerm, Pageable pageable) {
        log.debug("Searching products with term: {}", searchTerm);
        return productRepository.searchByNameOrDescription(searchTerm, pageable);
    }

    /**
     * Get products by category ID.
     */
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching products by category ID: {}", categoryId);
        return listingCache.getProductsByCategory(categoryId, pageable).toPage(pageable);
    }

    /**
     * Get products by category including subcategories.
     */
    public Page<Product> getProductsByCategoryIncludingSubcategories(Long categoryId, Pageable pageable) {
        log.debug("Fetching products by category ID (including subcategories): {}", categoryId);
        var category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CategoryNotFoundException("Category not found with ID: " + categoryId));
        return productRepository.findByCategoryOrSubcategories(category, pageable);
    }

    /**
     * Get products within price range.
     */
    public Page<Product> getProductsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        log.debug("Fetching products by price range: {} - {}", minPrice, maxPrice);
        return productRepository.findByPriceBetween(minPrice, maxPrice, pageable);
    }

    /**
     * Advanced search with multiple filters.
     */
    public Page<ProductResponse> advancedSearch(String searchTerm, Long categoryId, BigDecimal minPrice,
                                                BigDecimal maxPrice, boolean activeOnly, boolean inStockOnly,
                                                Pageable pageable) {
        log.debug("Advanced search - term: {}, categoryId: {}, priceRange: {}-{}, activeOnly: {}, inStockOnly: {}",
            searchTerm, categoryId, minPrice, maxPrice, activeOnly, inStockOnly);
        return productRepository.advancedSearch(searchTerm, categoryId, minPrice, maxPrice,
            activeOnly, inStockOnly, pageable).map(productMapper::toResponse);
    }

    /**
     * Get products with low stock.
     */
    public List<Product> getLowStockProducts(int threshold) {
        log.debug("Fetching low stock products with threshold: {}", threshold);
        return productRepository.findLowStockProducts(threshold);
    }

    /**
     * Get featured products.
     */
    public Page<ProductResponse> getFeaturedProducts(Pageable pageable) {
        log.debug("Fetching featured products");
        return listingCache.getFeaturedProducts(pageable).toPage(pageable);
    }

    /**
     * Create a new product.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", allEntries = true),
        @CacheEvict(value = ProductListingCache.LISTINGS_CACHE, allEntries = true)
    })
    public Product createProduct(Product product) {
        log.info("Creating new product: {}", product.getSku());

        // Validate SKU uniqueness
        if (productRepository.findBySku(product.getSku()).isPresent()) {
            throw new ProductAlreadyExistsException("Product with SKU " + product.getSku() + " already exists");
        }

        // Validate category if provided
        if (product.getCategory() != null && product.getCategory().getId() != null) {
            var category = categoryRepository.findById(product.getCategory().getId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);

        // Publish event
        eventPublisher.publishProductCreated(saved);

        return saved;
    }

    /**
     * Update an existing product's catalog attributes.
     *
     * <p><b>stockQuantity is deliberately NOT updated here.</b> Stock movement is
     * owned by inventory-service (reservations under pessimistic locking, restock,
     * refund restoration, reorder alerts — see {@code InventoryService}); this
     * service keeps only a catalog display snapshot that drives the in-stock
     * listing filters. A general PUT carries whatever stock value the client read
     * when it opened its form, so writing it back would blindly overwrite any
     * change made in between — a lost update against a number this service does
     * not own. Stock is seeded on create and moved afterwards only through
     * {@link #updateStockQuantity(Long, int)} (PATCH /api/products/{id}/stock),
     * which makes the intent explicit and auditable.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", key = "'id:' + #id"),
        @CacheEvict(value = ProductListingCache.LISTINGS_CACHE, allEntries = true)
    })
    public Product updateProduct(Long id, Product productDetails) {
        log.info("Updating product with ID: {}", id);

        Product product = getProductById(id);

        // Update fields (stockQuantity excluded by design — see javadoc)
        product.setName(productDetails.getName());
        product.setDescription(productDetails.getDescription());
        product.setPrice(productDetails.getPrice());
        product.setCurrency(productDetails.getCurrency());
        product.setDimensions(productDetails.getDimensions());
        product.setActive(productDetails.getActive());
        product.setImages(productDetails.getImages());

        // Update category if provided
        if (productDetails.getCategory() != null && productDetails.getCategory().getId() != null) {
            var category = categoryRepository.findById(productDetails.getCategory().getId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found"));
            product.setCategory(category);
        }

        Product updated = productRepository.save(product);

        // Publish event
        eventPublisher.publishProductUpdated(updated);

        return updated;
    }

    /**
     * Set the catalog stock snapshot for a product — the ONLY write path for
     * {@code stockQuantity} after creation (see {@link #updateProduct}).
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", key = "'id:' + #id"),
        @CacheEvict(value = ProductListingCache.LISTINGS_CACHE, allEntries = true)
    })
    public Product updateStockQuantity(Long id, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative: " + quantity);
        }
        log.info("Updating stock quantity for product ID: {} to {}", id, quantity);

        Product product = getProductById(id);
        product.setStockQuantity(quantity);

        return productRepository.save(product);
    }

    /**
     * Delete a product (soft delete by marking as inactive).
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", key = "'id:' + #id"),
        @CacheEvict(value = ProductListingCache.LISTINGS_CACHE, allEntries = true)
    })
    public void deleteProduct(Long id) {
        log.info("Deleting product with ID: {}", id);

        Product product = getProductById(id);
        product.setActive(false);
        productRepository.save(product);
    }

    /**
     * Hard delete a product (permanent deletion).
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "products", key = "'id:' + #id"),
        @CacheEvict(value = ProductListingCache.LISTINGS_CACHE, allEntries = true)
    })
    public void hardDeleteProduct(Long id) {
        log.warn("Hard deleting product with ID: {}", id);
        productRepository.deleteById(id);
    }

    // Custom exceptions
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }

    public static class ProductAlreadyExistsException extends RuntimeException {
        public ProductAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class CategoryNotFoundException extends RuntimeException {
        public CategoryNotFoundException(String message) {
            super(message);
        }
    }
}
