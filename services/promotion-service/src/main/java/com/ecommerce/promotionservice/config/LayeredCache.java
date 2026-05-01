package com.ecommerce.promotionservice.config;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;

import java.util.concurrent.Callable;

/**
 * Two-tier cache: L1 (Caffeine, in-process) in front of L2 (Redis, distributed).
 *
 * Read path:
 *   1. Check L1 (Caffeine) — local, sub-microsecond.
 *   2. Miss → check L2 (Redis) — network round-trip.
 *   3. Miss → call the supplier (DB).
 *   On L2 hit, the value is back-filled into L1 so subsequent local reads are free.
 *
 * Write path:
 *   put() / evict() write through to BOTH layers so other pods reading from Redis
 *   see the update (best-effort consistency — L1 on other pods still expires on TTL).
 *
 * Single-flight (stampede mitigation):
 *   {@link #get(Object, Callable)} delegates to L1's get(key, valueLoader), which
 *   Caffeine implements as Cache.get(key, k -> compute(k)) — concurrent loads for
 *   the same key inside one pod are deduplicated to a single supplier invocation.
 *   This is the dedup primitive used by {@code @Cacheable(sync = true)}.
 */
public class LayeredCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache l1;
    private final Cache l2;

    public LayeredCache(String name, Cache l1, Cache l2) {
        super(true);
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
            l1.put(key, value);
            return value;
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Cache.ValueWrapper l1Hit = l1.get(key);
        if (l1Hit != null) {
            @SuppressWarnings("unchecked")
            T value = (T) l1Hit.get();
            return value;
        }
        return l1.get(key, () -> {
            Cache.ValueWrapper l2Hit = l2.get(key);
            if (l2Hit != null) {
                @SuppressWarnings("unchecked")
                T cached = (T) l2Hit.get();
                return cached;
            }
            T loaded = valueLoader.call();
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
