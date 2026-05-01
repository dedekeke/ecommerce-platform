package com.ecommerce.productservice.config;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.util.concurrent.Callable;

/**
 * Two-tier cache: L1 (Caffeine, in-process) in front of L2 (Redis, distributed).
 *
 * Read path:
 *   1. Check L1 (Caffeine) — local, sub-microsecond.
 *   2. Miss → check L2 (Redis) — network round-trip, 0.5–2 ms typical.
 *   3. Miss → call the supplier (DB).
 *   On L2 hit, the value is back-filled into L1 so subsequent local reads are free.
 *
 * Write path:
 *   put() / evict() write through to BOTH layers so other pods reading from Redis
 *   see the update (best-effort consistency — L1 on other pods still expires on TTL).
 *
 * Single-flight (stampede mitigation):
 *   The {@link #get(Object, Callable)} overload delegates to the L1 cache's
 *   {@code get(key, valueLoader)} which is implemented by Caffeine's LoadingCache
 *   semantics (Spring's CaffeineCache.get(Object, Callable) wraps Caffeine's
 *   Cache.get(key, k -> compute(k))). Concurrent loads for the same key inside
 *   one pod are deduplicated to a single supplier invocation.
 *   This is the dedup primitive used by {@code @Cacheable(sync = true)}.
 */
public class LayeredCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache l1;
    private final Cache l2;

    public LayeredCache(String name, Cache l1, Cache l2) {
        super(true); // allow null values via NullValue marker
        this.name = name;
        this.l1 = l1;
        this.l2 = l2;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        // Expose the L1 cache as the native handle — diagnostics/tests use this.
        return l1.getNativeCache();
    }

    @Override
    protected Object lookup(Object key) {
        Cache.ValueWrapper l1Hit = l1.get(key);
        if (l1Hit != null) {
            return l1Hit.get();
        }
        Cache.ValueWrapper l2Hit = l2.get(key);
        if (l2Hit != null) {
            Object value = l2Hit.get();
            // Back-fill L1 so subsequent reads inside the TTL window stay in-process.
            l1.put(key, value);
            return value;
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        // Fast path: L1 hit.
        Cache.ValueWrapper l1Hit = l1.get(key);
        if (l1Hit != null) {
            @SuppressWarnings("unchecked")
            T value = (T) l1Hit.get();
            return value;
        }
        // Slow path: delegate to L1's loader for single-flight semantics
        // (Caffeine dedups concurrent loads for the same key within this pod).
        // Inside the loader we consult L2 first; only on L2-miss do we hit the DB.
        return l1.get(key, () -> {
            Cache.ValueWrapper l2Hit = l2.get(key);
            if (l2Hit != null) {
                @SuppressWarnings("unchecked")
                T cached = (T) l2Hit.get();
                return cached;
            }
            T loaded = valueLoader.call();
            // Promote to L2 so other pods see the value without re-querying the DB.
            l2.put(key, loaded);
            return loaded;
        });
    }

    @Override
    public void put(Object key, Object value) {
        l2.put(key, value);
        l1.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper existing = l2.putIfAbsent(key, value);
        if (existing == null) {
            l1.put(key, value);
            return null;
        }
        // Mirror to L1 so future local reads avoid Redis.
        l1.put(key, existing.get());
        return existing;
    }

    @Override
    public void evict(Object key) {
        l1.evict(key);
        l2.evict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean l2Removed = l2.evictIfPresent(key);
        boolean l1Removed = l1.evictIfPresent(key);
        return l1Removed || l2Removed;
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
    }

    @Override
    public boolean invalidate() {
        boolean l2Invalidated = l2.invalidate();
        boolean l1Invalidated = l1.invalidate();
        return l1Invalidated || l2Invalidated;
    }
}
