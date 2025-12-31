package com.ecommerce.productservice.service;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * Get product by ID.
     * Note: Caching temporarily disabled due to serialization issues with Hibernate proxies.
     */
    // @Cacheable(value = "products", key = "#id") // TODO: Re-enable after fixing DTO caching
    public Product getProductById(Long id) {
        log.debug("Fetching product by ID: {}", id);
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with ID: " + id));
    }

    /**
     * Get product by SKU.
     * Note: Caching temporarily disabled due to serialization issues with Hibernate proxies.
     */
    // @Cacheable(value = "products", key = "#sku") // TODO: Re-enable after fixing DTO caching
    public Product getProductBySku(String sku) {
        log.debug("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku)
            .orElseThrow(() -> new ProductNotFoundException("Product not found with SKU: " + sku));
    }

    /**
     * Get all products with pagination.
     */
    public Page<Product> getAllProducts(Pageable pageable) {
        log.debug("Fetching all products with pagination: {}", pageable);
        return productRepository.findAll(pageable);
    }

    /**
     * Get active products only.
     */
    public Page<Product> getActiveProducts(Pageable pageable) {
        log.debug("Fetching active products with pagination: {}", pageable);
        return productRepository.findByActiveTrue(pageable);
    }

    /**
     * Get available products (active and in stock).
     */
    public Page<Product> getAvailableProducts(Pageable pageable) {
        log.debug("Fetching available products with pagination: {}", pageable);
        return productRepository.findAvailable(pageable);
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
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Fetching products by category ID: {}", categoryId);
        return productRepository.findByCategoryId(categoryId, pageable);
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
    public Page<Product> advancedSearch(String searchTerm, Long categoryId, BigDecimal minPrice,
                                        BigDecimal maxPrice, boolean activeOnly, boolean inStockOnly,
                                        Pageable pageable) {
        log.debug("Advanced search - term: {}, categoryId: {}, priceRange: {}-{}, activeOnly: {}, inStockOnly: {}",
            searchTerm, categoryId, minPrice, maxPrice, activeOnly, inStockOnly);
        return productRepository.advancedSearch(searchTerm, categoryId, minPrice, maxPrice,
            activeOnly, inStockOnly, pageable);
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
    public Page<Product> getFeaturedProducts(Pageable pageable) {
        log.debug("Fetching featured products");
        return productRepository.findFeaturedProducts(pageable);
    }

    /**
     * Create a new product.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
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
     * Update an existing product.
     */
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public Product updateProduct(Long id, Product productDetails) {
        log.info("Updating product with ID: {}", id);

        Product product = getProductById(id);

        // Update fields
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
     * Update product stock quantity.
     */
    @Transactional
    @CacheEvict(value = "products", key = "#id")
    public Product updateStockQuantity(Long id, int quantity) {
        log.info("Updating stock quantity for product ID: {} to {}", id, quantity);

        Product product = getProductById(id);
        product.setStockQuantity(quantity);

        return productRepository.save(product);
    }

    /**
     * Delete a product (soft delete by marking as inactive).
     */
    @Transactional
    @CacheEvict(value = "products", key = "#id")
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
    @CacheEvict(value = "products", key = "#id")
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
