package cachetestsupport;

import com.ecommerce.productservice.config.LayeredCacheManager;
import com.ecommerce.productservice.event.ProductEventPublisher;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
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
 * Spring config used ONLY by the cache-stampede test in the product-service
 * test sources. Lives in the {@code cachetestsupport} package — outside
 * {@code com.ecommerce.productservice} — so that @DataJpaTest /
 * @SpringBootTest slices that component-scan from the main app's base
 * package will NOT pick it up. The stampede test imports it explicitly via
 * @SpringJUnitConfig — that is the only entry point.
 */
@Configuration
@EnableCaching
public class CacheStampedeTestConfig {

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
        CaffeineCacheManager mgr = new CaffeineCacheManager("products");
        mgr.setCaffeine(Caffeine.newBuilder().maximumSize(1_000));
        return mgr;
    }

    @Bean
    public CacheManager redisStandIn() {
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
