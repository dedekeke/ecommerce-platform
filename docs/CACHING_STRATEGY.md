# Redis Caching Strategy

This document describes the caching architecture and strategy implemented across the e-commerce platform microservices.

## Overview

The platform uses Redis as a distributed caching layer to improve read performance and reduce database load. Spring Cache abstraction is used with Redis as the backing store, implementing a **cache-aside** pattern.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Product Svc    │     │   User Svc      │     │ Promotion Svc   │
│  (Redis Cache)  │     │  (Redis Cache)  │     │ (Redis Cache)   │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │        Redis            │
                    │    (Port 6379)          │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │    Redis Exporter       │
                    │    (Port 9121)          │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      Prometheus         │
                    │    (Metrics Store)      │
                    └────────────┬────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │       Grafana           │
                    │   (Visualization)       │
                    └─────────────────────────┘
```

## Cache Configurations by Service

### Product Service

| Cache Name | TTL | Purpose |
|------------|-----|---------|
| `products` | 1 hour | Individual product details by ID |
| `categories` | 2 hours | Category data by ID |
| `product-search` | 15 minutes | Search results (volatile, frequent updates) |
| `category-tree` | 2 hours | Full category hierarchy |
| `popular-products` | 30 minutes | Frequently accessed product lists |

**Configuration**: `services/product-service/src/main/java/com/ecommerce/productservice/config/CacheConfig.java`

### User Service

| Cache Name | TTL | Purpose |
|------------|-----|---------|
| `users` | 15 minutes | User data by ID |
| `users-by-auth0` | 15 minutes | User lookup by Auth0 ID |
| `users-by-email` | 15 minutes | User lookup by email |
| `user-addresses` | 30 minutes | User address data |

**Configuration**: `services/user-service/src/main/java/com/ecommerce/userservice/config/CacheConfig.java`

### Promotion Service

| Cache Name | TTL | Purpose |
|------------|-----|---------|
| `promotions` | 30 minutes | Individual promotion details |
| `active-promotions` | 30 minutes | List of currently active promotions |
| `promotion-codes` | 30 minutes | Promotion lookup by code |

**Configuration**: `services/promotion-service/src/main/java/com/ecommerce/promotionservice/config/CacheConfig.java`

## Cache Patterns

### Cache-Aside Pattern

The platform implements the cache-aside pattern using Spring Cache annotations:

```java
// Read: Check cache first, load from DB if miss
@Cacheable(value = "products", key = "#id", unless = "#result == null")
public Product getProductById(Long id) {
    return productRepository.findById(id).orElse(null);
}

// Write: Evict cache on update
@CacheEvict(value = "products", key = "#product.id")
public Product updateProduct(Product product) {
    return productRepository.save(product);
}
```

### Multi-Cache Eviction

For entities with multiple lookup paths (e.g., users by ID, email, Auth0 ID):

```java
@Caching(evict = {
    @CacheEvict(value = "users", key = "#user.id"),
    @CacheEvict(value = "users-by-auth0", key = "#user.auth0Id"),
    @CacheEvict(value = "users-by-email", key = "#user.email")
})
public User updateUser(User user) {
    return userRepository.save(user);
}
```

## Cache Warming

Product Service implements cache warming on startup to preload frequently accessed data:

**File**: `services/product-service/src/main/java/com/ecommerce/productservice/config/CacheWarmerService.java`

On application startup:
1. Loads all root categories into cache
2. Loads full category tree
3. Preloads top 100 products

This reduces cache miss latency after deployments.

## Serialization

All services use JSON serialization for cached values:

```java
RedisCacheConfiguration.defaultCacheConfig()
    .serializeKeysWith(RedisSerializationContext.SerializationPair
        .fromSerializer(new StringRedisSerializer()))
    .serializeValuesWith(RedisSerializationContext.SerializationPair
        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
```

Benefits:
- Human-readable cache entries for debugging
- Cross-language compatibility if needed
- Handles polymorphic types with `@class` metadata

## TTL Strategy

| Data Type | TTL | Rationale |
|-----------|-----|-----------|
| Static data (categories) | 2 hours | Rarely changes, safe to cache longer |
| Product details | 1 hour | Balance between freshness and performance |
| User profiles | 15 minutes | Moderate change frequency, security consideration |
| Search results | 15 minutes | Volatile, needs frequent refresh |
| Promotions | 30 minutes | Time-sensitive, moderate refresh |

## Monitoring

### Redis Exporter

The `redis-exporter` service exposes Redis metrics for Prometheus at port 9121.

### Grafana Dashboard

Access the Redis Monitoring dashboard in Grafana to view:

- **Redis Status**: Up/down indicator
- **Connected Clients**: Number of active connections
- **Memory Used**: Current memory consumption
- **Total Keys**: Number of cached entries
- **Cache Hit Rate**: Percentage of successful cache hits
- **Uptime**: Redis server uptime
- **Commands/sec**: Operations throughput
- **Hits vs Misses**: Cache effectiveness over time
- **Memory Usage Trend**: Memory consumption timeline
- **Keys by Database**: Distribution across Redis databases

### Key Metrics to Watch

| Metric | Healthy Range | Action if Exceeded |
|--------|---------------|-------------------|
| Hit Rate | > 90% | Review cache TTLs, add more caching |
| Memory Used | < 80% of limit | Scale Redis or review TTLs |
| Connected Clients | < 100 | Check for connection leaks |
| Commands/sec | < 10,000 | Consider Redis cluster |

## Environment Configuration

### Application Properties

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

### Docker Compose

Redis runs as a container with:
- Port: 6379
- Health check enabled
- Data persistence via volume mount

## Best Practices

1. **Always use `unless` clause**: Prevent caching null values
   ```java
   @Cacheable(value = "users", key = "#id", unless = "#result == null")
   ```

2. **Evict on write operations**: Keep cache consistent with database
   ```java
   @CacheEvict(value = "products", key = "#id")
   public void deleteProduct(Long id) { ... }
   ```

3. **Use meaningful cache names**: Prefix with domain/service
   ```
   products, product-search, user-addresses
   ```

4. **Short TTL for sensitive data**: User data uses 15-minute TTL

5. **Monitor cache effectiveness**: Use Grafana dashboard to track hit rates

## Troubleshooting

### Cache Not Working

1. Verify Redis is running: `docker ps | grep redis`
2. Check connection: `redis-cli ping`
3. Verify `spring.cache.type: redis` in application.yml
4. Ensure `@EnableCaching` is present in config

### High Memory Usage

1. Review TTL settings - reduce if possible
2. Check for runaway cache population
3. Monitor key count trends in Grafana
4. Consider adding `maxmemory` policy to Redis

### Low Hit Rate

1. Verify cache keys are consistent
2. Check if TTL is too short for access patterns
3. Review cache eviction logic - avoid over-eviction
4. Add more @Cacheable annotations to hot paths

### Stale Data

1. Verify @CacheEvict on all write operations
2. Check for direct database updates bypassing service layer
3. Review TTL - may need shorter duration
4. Consider cache invalidation via events for cross-service updates
