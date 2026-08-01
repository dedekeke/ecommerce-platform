package com.ecommerce.productservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the "product-listings" cache is registered with the env-tunable TTL
 * ({@code product-cache.listings-ttl-seconds}). Asserts the per-cache config map
 * directly ({@link CacheConfig#buildCacheConfigurations}) rather than through a
 * built {@code RedisCacheManager}, whose {@code getCacheConfigurations()} is
 * unusable once {@code transactionAware()} wraps the caches.
 */
class CacheConfigTtlTest {

    private Map<String, RedisCacheConfiguration> configsFor(long ttlSeconds) {
        CacheConfig config = new CacheConfig();
        ReflectionTestUtils.setField(config, "listingsTtlSeconds", ttlSeconds);
        return config.buildCacheConfigurations(RedisCacheConfiguration.defaultCacheConfig());
    }

    @Test
    void should_applyConfiguredTtl_toProductListingsCache() {
        Map<String, RedisCacheConfiguration> configs = configsFor(30L);

        assertThat(configs).containsKey("product-listings");
        assertThat(configs.get("product-listings").getTtl()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void should_keepListingsTtlShort_relativeToByIdCache() {
        Map<String, RedisCacheConfiguration> configs = configsFor(45L);

        Duration listingsTtl = configs.get("product-listings").getTtl();
        Duration productsTtl = configs.get("products").getTtl();

        assertThat(listingsTtl).isEqualTo(Duration.ofSeconds(45));
        assertThat(listingsTtl).isLessThan(productsTtl);
    }
}
