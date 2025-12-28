package com.ecommerce.userservice.config;

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
 * Redis cache configuration for User Service
 *
 * Cache TTLs are optimized for user profile data:
 * - User profiles: 15 minutes (balance between freshness and performance)
 * - User by Auth0 ID: 15 minutes (same as profiles, main lookup)
 * - User addresses: 30 minutes (less frequently changed)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(15)) // Default TTL: 15 minutes
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

        // Users cache: 15 minutes
        cacheConfigurations.put("users",
            defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Users by Auth0 ID: 15 minutes (main lookup pattern)
        cacheConfigurations.put("users-by-auth0",
            defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // Users by email: 15 minutes
        cacheConfigurations.put("users-by-email",
            defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // User addresses: 30 minutes (less frequently changed)
        cacheConfigurations.put("user-addresses",
            defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();
    }
}
