package com.ecommerce.productservice.service;

import cachetestsupport.ProductListingCacheTestConfig;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioural tests for the short-TTL product listing cache:
 *   1. repeated reads of the same page collapse to a single DB query,
 *   2. a write (create) invalidates the listing cache so the next read re-queries.
 *
 * Runs against the same layered cache wiring as production
 * ({@link ProductListingCacheTestConfig}) with a mocked repository, so the
 * assertions are on real cache behaviour rather than a stub.
 */
@SpringJUnitConfig(ProductListingCacheTestConfig.class)
class ProductListingCacheTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void clearCachesAndMocks() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        reset(productRepository);
    }

    private Product sampleProduct() {
        return Product.builder()
                .id(1L)
                .sku("LAPTOP-001")
                .name("Dell XPS 13")
                .price(new BigDecimal("1299.99"))
                .currency("USD")
                .stockQuantity(10)
                .active(true)
                .build();
    }

    private Page<Product> singlePage() {
        return new PageImpl<>(List.of(sampleProduct()), PAGE, 1);
    }

    @Test
    void should_serveSecondListingReadFromCache_when_samePageRequestedTwice() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(singlePage());

        Page<Product> first = productService.getAllProducts(PAGE);
        Page<Product> second = productService.getAllProducts(PAGE);

        assertThat(first.getContent()).hasSize(1);
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.getTotalElements()).isEqualTo(1);
        verify(productRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void should_preservePageMetadata_when_servedFromCache() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(singlePage());

        productService.getAllProducts(PAGE);
        Page<Product> cached = productService.getAllProducts(PAGE);

        assertThat(cached.getNumber()).isEqualTo(0);
        assertThat(cached.getSize()).isEqualTo(20);
        assertThat(cached.getTotalElements()).isEqualTo(1);
        assertThat(cached.getContent().get(0).getSku()).isEqualTo("LAPTOP-001");
    }

    @Test
    void should_evictListingCache_when_productCreated() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(singlePage());
        when(productRepository.findBySku(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        // Warm the listing cache.
        productService.getAllProducts(PAGE);
        // A write must invalidate all cached listing pages.
        productService.createProduct(sampleProduct());
        // Next read is a fresh DB query.
        productService.getAllProducts(PAGE);

        verify(productRepository, times(2)).findAll(any(Pageable.class));
    }

    @Test
    void should_cacheDistinctPagesSeparately_when_differentPageRequested() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(singlePage());

        productService.getAllProducts(PageRequest.of(0, 20));
        productService.getAllProducts(PageRequest.of(1, 20));

        // Distinct keys → two DB queries, no cross-page collision.
        verify(productRepository, times(2)).findAll(any(Pageable.class));
    }
}
