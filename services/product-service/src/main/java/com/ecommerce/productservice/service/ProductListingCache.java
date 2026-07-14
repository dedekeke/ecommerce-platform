package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.CachedProductPage;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Short-TTL cache in front of the paginated product listing queries (the browse
 * hot path). Kept as a <b>separate bean</b> from {@link ProductService} on
 * purpose: {@code @Cacheable} only fires through the Spring proxy, so calling a
 * cached method from within the same bean (self-invocation) would silently skip
 * the cache. {@code ProductService} injects and delegates to this bean instead.
 *
 * <p>Each listing is cached under the "product-listings" cache with a key that
 * encodes the full pageable + filter tuple, so different pages / sorts / filters
 * never collide. {@code sync = true} gives the same single-flight stampede
 * protection used by the by-id/by-sku caches (one DB load per key per pod).
 *
 * <p>The cached value is a {@link CachedProductPage} of already-mapped
 * {@link ProductResponse} DTOs. Mapping runs here, inside the transactional
 * (session-open) method, so the mapper can safely traverse the LAZY
 * {@code category} (parent chain, children) and the value handed to the cache is a
 * plain POJO graph — no Hibernate proxies or {@code PersistentSet} that would
 * break Redis serialization or throw on a session-less cache hit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductListingCache {

    static final String LISTINGS_CACHE = "product-listings";

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Cacheable(value = LISTINGS_CACHE,
        key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort",
        sync = true)
    public CachedProductPage getAllProducts(Pageable pageable) {
        log.debug("Cache miss - loading all products page: {}", pageable);
        return snapshot(productRepository.findAll(pageable));
    }

    @Cacheable(value = LISTINGS_CACHE,
        key = "'active:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort",
        sync = true)
    public CachedProductPage getActiveProducts(Pageable pageable) {
        log.debug("Cache miss - loading active products page: {}", pageable);
        return snapshot(productRepository.findByActiveTrue(pageable));
    }

    @Cacheable(value = LISTINGS_CACHE,
        key = "'category:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort",
        sync = true)
    public CachedProductPage getProductsByCategory(Long categoryId, Pageable pageable) {
        log.debug("Cache miss - loading products for category {} page: {}", categoryId, pageable);
        return snapshot(productRepository.findByCategoryId(categoryId, pageable));
    }

    @Cacheable(value = LISTINGS_CACHE,
        key = "'featured:' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort",
        sync = true)
    public CachedProductPage getFeaturedProducts(Pageable pageable) {
        log.debug("Cache miss - loading featured products page: {}", pageable);
        return snapshot(productRepository.findFeaturedProducts(pageable));
    }

    /**
     * Maps the page to DTOs while the Hibernate session is still open, producing
     * the cache-safe, session-independent snapshot.
     */
    private CachedProductPage snapshot(Page<Product> page) {
        List<ProductResponse> content = page.getContent().stream()
            .map(productMapper::toResponse)
            .toList();
        return CachedProductPage.of(content, page.getTotalElements());
    }
}
