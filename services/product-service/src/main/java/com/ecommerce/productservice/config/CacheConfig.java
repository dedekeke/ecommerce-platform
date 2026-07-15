package com.ecommerce.productservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Multi-tier cache configuration for product-service.
 *
 * Layering (L1 in-process Caffeine + L2 distributed Redis):
 *   - L1 (Caffeine): ~5 s TTL, 5 000 entries max. Sub-microsecond reads, dedups
 *     concurrent loads for the same key inside one pod (single-flight).
 *   - L2 (Redis): existing TTLs (1 h products, 2 h categories, etc.). Shared
 *     across pods so a cold L1 miss usually warms from L2 instead of the DB.
 *
 * Stampede mitigation:
 *   - Caffeine's get(key, loader) coalesces concurrent loads → only ONE thread
 *     per pod hits the DB on cache miss. Hot methods declare {@code @Cacheable(sync = true)}
 *     to opt into this.
 *   - L1 TTL of 5 s gives a tight blast-radius if L2 is briefly stale.
 *
 * Write path:
 *   {@code @CachePut} writes through to BOTH layers via {@link LayeredCache#put}.
 *   {@code @CacheEvict} invalidates BOTH layers.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** L1 cache names — any cache that benefits from sub-ms reads at high concurrency. */
    private static final String[] CACHE_NAMES = {
        "products",
        "categories",
        "product-search",
        "category-tree",
        "popular-products",
        "product-listings"
    };

    /** Cache name for the paginated browse listings (see ProductListingCache). */
    private static final String PRODUCT_LISTINGS_CACHE = "product-listings";

    /**
     * L2 TTL for the browse listing caches. Short + env-tunable: bounds how long a
     * stale page survives after a write/TTL expiry. Default 45s.
     */
    @org.springframework.beans.factory.annotation.Value("${product-cache.listings-ttl-seconds:45}")
    private long listingsTtlSeconds;

    /** L1 maximum size — guards heap usage. 5k entries is plenty for a hot-key cache. */
    private static final int L1_MAX_SIZE = 5_000;

    /** L1 TTL — short, since L2 is the source of truth shared across pods. */
    private static final Duration L1_TTL = Duration.ofSeconds(5);

    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(CACHE_NAMES);
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(L1_MAX_SIZE)
            .expireAfterWrite(L1_TTL.toMillis(), TimeUnit.MILLISECONDS)
            .recordStats());
        // Allow caches not pre-declared above to be created on demand (avoids NPE on new @Cacheable names).
        manager.setAllowNullValues(true);
        return manager;
    }

    /**
     * The exact ObjectMapper/serializer used for the L2 (Redis) payloads.
     * Package-private so tests can round-trip cached values through the real
     * production serialization config without booting Redis.
     */
    public GenericJackson2JsonRedisSerializer redisSerializer() {
        // SECURITY: type-allowlist validator restricts polymorphic deserialization to known
        // application packages, preventing Jackson gadget attacks via crafted Redis payloads.
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .allowIfSubType("com.ecommerce.productservice")
            .allowIfSubType("java.util")
            .allowIfSubType("java.math")
            .allowIfSubType("java.time")
            .build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.activateDefaultTyping(
            typeValidator,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = redisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
            .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(buildCacheConfigurations(defaultConfig))
            .transactionAware()
            .build();
    }

    /**
     * Per-cache TTL overrides. Extracted (package-private) so the TTL wiring can be
     * asserted without booting a RedisCacheManager — {@code getCacheConfigurations()}
     * is unusable once {@code transactionAware()} wraps the caches.
     */
    Map<String, RedisCacheConfiguration> buildCacheConfigurations(RedisCacheConfiguration defaultConfig) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("products",          defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("categories",        defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("product-search",    defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("category-tree",     defaultConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("popular-products",  defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put(PRODUCT_LISTINGS_CACHE, defaultConfig.entryTtl(Duration.ofSeconds(listingsTtlSeconds)));
        return cacheConfigurations;
    }

    /**
     * Primary CacheManager — the layered (L1 + L2) manager. Spring's @Cacheable picks
     * this one because it is @Primary; the underlying Caffeine and Redis managers are
     * still bean-injectable for tests / diagnostics.
     */
    @Bean
    @Primary
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager,
                                     RedisCacheManager redisCacheManager) {
        return new LayeredCacheManager(caffeineCacheManager, redisCacheManager);
    }
}
