package com.ecommerce.productservice.service;

import com.ecommerce.productservice.config.LayeredCacheManager;
import com.ecommerce.productservice.event.ProductEventPublisher;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Uses a tiny Spring test slice so the @Cacheable proxy is real (the cache
 * interceptor is wired by Spring's @EnableCaching).
 */
@SpringJUnitConfig(ProductServiceCacheStampedeTest.TestConfig.class)
class ProductServiceCacheStampedeTest {

    @TestConfiguration
    @EnableCaching
    static class TestConfig {

        @Bean
        public CaffeineCacheManager caffeineCacheManager() {
            CaffeineCacheManager mgr = new CaffeineCacheManager("products");
            mgr.setCaffeine(Caffeine.newBuilder().maximumSize(1_000));
            return mgr;
        }

        @Bean
        public CacheManager redisStandIn() {
            // In-memory L2 stand-in so the test stays hermetic. The single-flight
            // property under test is provided by L1 (Caffeine), not L2.
            return new ConcurrentMapCacheManager("products");
        }

        @Bean
        @Primary
        public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager,
                                         CacheManager redisStandIn) {
            return new LayeredCacheManager(caffeineCacheManager, redisStandIn);
        }

        @Bean
        public ProductService productService(ProductRepository productRepository,
                                             CategoryRepository categoryRepository,
                                             ProductEventPublisher eventPublisher) {
            return new ProductService(productRepository, categoryRepository, eventPublisher);
        }
    }

    @MockBean private ProductRepository productRepository;
    @MockBean private CategoryRepository categoryRepository;
    @MockBean private ProductEventPublisher eventPublisher;

    @Autowired private ProductService productService;
    @Autowired private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        // Clear any state leftover from previous tests.
        var cache = cacheManager.getCache("products");
        if (cache != null) cache.clear();
    }

    @Test
    void should_callRepositoryExactlyOnce_when_100ThreadsConcurrentlyMissCacheForSameKey() throws Exception {
        // Arrange: a single product, slow DB to maximise the chance threads contend on the loader.
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

        // Act
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

        // Assert
        assertThat(allFinished).as("all 100 threads completed").isTrue();
        assertThat(successCount.get()).as("all threads got the product").isEqualTo(threadCount);
        assertThat(repoCalls.get())
            .as("100 concurrent threads should collapse to ONE DB call (single-flight)")
            .isEqualTo(1);
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    void should_serveSubsequentReadsFromL1_when_keyAlreadyCached() {
        // Arrange
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

        // Act
        Product first = productService.getProductById(productId);
        Product second = productService.getProductById(productId);
        Product third = productService.getProductById(productId);

        // Assert
        assertThat(first.getId()).isEqualTo(productId);
        assertThat(second.getId()).isEqualTo(productId);
        assertThat(third.getId()).isEqualTo(productId);
        verify(productRepository, times(1)).findById(productId);
    }
}
