package com.ecommerce.productservice.service;

import cachetestsupport.CacheStampedeTestConfig;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the cache-stampede protection wired up in {@link ProductService}.
 *
 * Scenario: 100 concurrent threads call {@code getProductById(1L)} when the cache
 * is empty. With {@code @Cacheable(sync = true)} backed by the LayeredCacheManager
 * (Caffeine L1 in front of an in-memory L2 stand-in), Caffeine must dedup the
 * concurrent loads, so {@code productRepository.findById(1L)} is invoked exactly
 * ONCE.
 *
 * The Spring config lives in the {@code cachetestsupport} package so the
 * @SpringBootApplication's component scan (rooted at
 * {@code com.ecommerce.productservice}) does NOT pick it up — other test
 * slices in this module load the production CacheConfig untouched.
 */
@SpringJUnitConfig(CacheStampedeTestConfig.class)
class ProductServiceCacheStampedeTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void clearCacheAndMocks() {
        var cache = cacheManager.getCache("products");
        if (cache != null) cache.clear();
        reset(productRepository);
    }

    @Test
    void should_callRepositoryExactlyOnce_when_100ThreadsConcurrentlyMissCacheForSameKey() throws Exception {
        Long productId = 1L;
        Product product = Product.builder()
            .id(productId)
            .sku("LAPTOP-001")
            .name("Dell XPS 13")
            .price(new BigDecimal("1299.99"))
            .currency("USD")
            .stockQuantity(10)
            .active(true)
            .build();

        AtomicInteger repoCalls = new AtomicInteger(0);
        when(productRepository.findById(productId)).thenAnswer(inv -> {
            repoCalls.incrementAndGet();
            // Simulate DB latency so all 100 threads pile up on the loader.
            Thread.sleep(50);
            return Optional.of(product);
        });

        int threadCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    Product p = productService.getProductById(productId);
                    if (p != null && productId.equals(p.getId())) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean allFinished = done.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(allFinished).as("all 100 threads completed").isTrue();
        assertThat(successCount.get()).as("all threads got the product").isEqualTo(threadCount);
        assertThat(repoCalls.get())
            .as("100 concurrent threads should collapse to ONE DB call (single-flight)")
            .isEqualTo(1);
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    void should_serveSubsequentReadsFromL1_when_keyAlreadyCached() {
        Long productId = 2L;
        Product product = Product.builder()
            .id(productId)
            .sku("PHONE-001")
            .name("iPhone 15 Pro")
            .price(new BigDecimal("999.99"))
            .currency("USD")
            .stockQuantity(5)
            .active(true)
            .build();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Product first = productService.getProductById(productId);
        Product second = productService.getProductById(productId);
        Product third = productService.getProductById(productId);

        assertThat(first.getId()).isEqualTo(productId);
        assertThat(second.getId()).isEqualTo(productId);
        assertThat(third.getId()).isEqualTo(productId);
        verify(productRepository, times(1)).findById(productId);
    }
}
