package com.ecommerce.productservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for Product Service
 *
 * Cache TTLs are optimized for read-heavy product catalog workload:
 * - Products: 1 hour (product data changes infrequently)
 * - Categories: 2 hours (category structure rarely changes)
 * - Product lists: 15 minutes (search results, filtered lists)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30)) // Default TTL: 30 minutes
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();

        // Custom cache configurations for different cache types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Products cache: 1 hour (product data is relatively stable)
        cacheConfigurations.put("products",
            defaultConfig.entryTtl(Duration.ofHours(1)));

        // Categories cache: 2 hours (category structure rarely changes)
        cacheConfigurations.put("categories",
            defaultConfig.entryTtl(Duration.ofHours(2)));

        // Product search results: 15 minutes (more dynamic)
        cacheConfigurations.put("product-search",
            defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Category tree cache: 2 hours (hierarchical data rarely changes)
        cacheConfigurations.put("category-tree",
            defaultConfig.entryTtl(Duration.ofHours(2)));

        // Popular products cache: 30 minutes (for homepage/recommendations)
        cacheConfigurations.put("popular-products",
            defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }
}
