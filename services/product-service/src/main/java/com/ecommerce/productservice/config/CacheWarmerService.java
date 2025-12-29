package com.ecommerce.productservice.config;

import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Cache warmer service that preloads frequently accessed data into Redis cache on startup.
 *
 * This reduces cache miss latency for the most common queries after deployment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheWarmerService {

    private final ProductService productService;
    private final CategoryService categoryService;

    private static final int WARM_UP_PRODUCT_COUNT = 100;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmUpCache() {
        log.info("Starting cache warm-up...");
        long startTime = System.currentTimeMillis();

        try {
            warmUpCategories();
            warmUpProducts();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Cache warm-up completed in {} ms", duration);
        } catch (Exception e) {
            log.error("Cache warm-up failed", e);
        }
    }

    private void warmUpCategories() {
        log.info("Warming up category cache...");
        try {
            // Load all root categories (triggers cache population)
            var rootCategories = categoryService.getRootCategories();
            log.info("Cached {} root categories", rootCategories.size());

            // Load category tree
            var allCategories = categoryService.getAllCategories();
            log.info("Cached {} total categories", allCategories.size());
        } catch (Exception e) {
            log.warn("Failed to warm up category cache: {}", e.getMessage());
        }
    }

    private void warmUpProducts() {
        log.info("Warming up product cache with top {} products...", WARM_UP_PRODUCT_COUNT);
        try {
            // Load first page of active products (most likely to be accessed)
            var products = productService.getAllProducts(PageRequest.of(0, WARM_UP_PRODUCT_COUNT));
            log.info("Cached {} products", products.getContent().size());

            // Optionally: Load individual products to populate by-id cache
            products.getContent().forEach(product -> {
                try {
                    productService.getProductById(product.getId());
                } catch (Exception e) {
                    log.debug("Failed to cache product {}: {}", product.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to warm up product cache: {}", e.getMessage());
        }
    }
}
