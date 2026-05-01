package com.ecommerce.promotionservice.config;

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
 * Multi-tier cache configuration for promotion-service.
 *
 * Layering (L1 in-process Caffeine + L2 distributed Redis):
 *   - L1 (Caffeine): ~5 s TTL, 5 000 entries max. Sub-microsecond reads, dedups
 *     concurrent loads for the same key inside one pod (single-flight).
 *   - L2 (Redis): existing TTLs (30 m promotions, 5 m active-promotions, ...).
 *
 * Stampede mitigation: hot @Cacheable methods opt into sync = true; Caffeine's
 * loader path coalesces concurrent loads.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String[] CACHE_NAMES = {
        "promotions",
        "active-promotions",
        "activePromotions",          // legacy name used in PromotionService
        "promotion-validations"
    };

    private static final int L1_MAX_SIZE = 5_000;
    private static final Duration L1_TTL = Duration.ofSeconds(5);

    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_NAMES);
        mgr.setCaffeine(Caffeine.newBuilder()
            .maximumSize(L1_MAX_SIZE)
            .expireAfterWrite(L1_TTL.toMillis(), TimeUnit.MILLISECONDS)
            .recordStats());
        mgr.setAllowNullValues(true);
        return mgr;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("promotions",             defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("active-promotions",      defaultConfig.entryTtl(Duration.ofMinutes(5)));
        // Legacy cache name still used by PromotionService.getAllActivePromotions.
        cacheConfigurations.put("activePromotions",       defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("promotion-validations",  defaultConfig.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }

    @Bean
    @Primary
    public CacheManager cacheManager(CaffeineCacheManager caffeineCacheManager,
                                     RedisCacheManager redisCacheManager) {
        return new LayeredCacheManager(caffeineCacheManager, redisCacheManager);
    }
}
