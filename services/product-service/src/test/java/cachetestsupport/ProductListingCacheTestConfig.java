package cachetestsupport;

import com.ecommerce.productservice.config.LayeredCacheManager;
import com.ecommerce.productservice.event.ProductEventPublisher;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.ecommerce.productservice.service.ProductListingCache;
import com.ecommerce.productservice.service.ProductService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring config for the product-listing cache tests. Lives in
 * {@code cachetestsupport} (outside {@code com.ecommerce.productservice}) so no
 * {@code @SpringBootTest} / slice component-scan picks it up; the listing-cache
 * test imports it explicitly. Mirrors the layered (L1 Caffeine + L2 in-memory
 * Redis stand-in) wiring of production so {@code @Cacheable}/{@code @CacheEvict}
 * behave exactly as they would at runtime, with a mocked repository so we can
 * assert DB hit counts.
 */
@Configuration
@EnableCaching
public class ProductListingCacheTestConfig {

    private static final String[] NAMES = {"products", "product-listings"};

    @Bean
    public ProductRepository productRepository() {
        return Mockito.mock(ProductRepository.class);
    }

    @Bean
    public CategoryRepository categoryRepository() {
        return Mockito.mock(CategoryRepository.class);
    }

    @Bean
    public ProductEventPublisher eventPublisher() {
        return Mockito.mock(ProductEventPublisher.class);
    }

    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(NAMES);
        mgr.setCaffeine(Caffeine.newBuilder().maximumSize(1_000));
        return mgr;
    }

    @Bean
    public CacheManager redisStandIn() {
        return new ConcurrentMapCacheManager(NAMES);
    }

    @Bean
    @Primary
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager,
                                     CacheManager redisStandIn) {
        return new LayeredCacheManager(caffeineCacheManager, redisStandIn);
    }

    @Bean
    public ProductMapper productMapper() {
        return new ProductMapper(new CategoryMapper());
    }

    @Bean
    public ProductListingCache productListingCache(ProductRepository productRepository, ProductMapper productMapper) {
        return new ProductListingCache(productRepository, productMapper);
    }

    @Bean
    public ProductService productService(ProductRepository productRepository,
                                         CategoryRepository categoryRepository,
                                         ProductEventPublisher eventPublisher,
                                         ProductListingCache productListingCache,
                                         ProductMapper productMapper) {
        return new ProductService(productRepository, categoryRepository, eventPublisher,
            productListingCache, productMapper);
    }
}
