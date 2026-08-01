package com.ecommerce.productservice.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CacheManager that returns a {@link LayeredCache} (L1 Caffeine + L2 Redis) for
 * each cache name, building it on-demand from the underlying L1/L2 managers.
 *
 * Why a custom manager rather than Spring's built-in CompositeCacheManager:
 *   CompositeCacheManager picks the FIRST matching cache from a list of managers
 *   — it does not layer them. We need a true two-tier read/write path
 *   (read L1, miss → read L2, miss → load; write to both), which requires a
 *   wrapping Cache type ({@link LayeredCache}). Hence: thin custom manager.
 *
 * Cache name resolution: any name registered in either the L1 or L2 manager is
 * served. Names known only to L2 still get a LayeredCache because we lazily ask
 * the L1 manager for a cache (Caffeine's manager auto-creates if configured to).
 */
public class LayeredCacheManager implements CacheManager {

    private final CacheManager l1Manager;
    private final CacheManager l2Manager;
    private final ConcurrentMap<String, LayeredCache> caches = new ConcurrentHashMap<>();

    public LayeredCacheManager(CacheManager l1Manager, CacheManager l2Manager) {
        this.l1Manager = l1Manager;
        this.l2Manager = l2Manager;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::buildLayeredCache);
    }

    private LayeredCache buildLayeredCache(String name) {
        Cache l1 = l1Manager.getCache(name);
        Cache l2 = l2Manager.getCache(name);
        if (l1 == null || l2 == null) {
            // Should not happen in practice: Caffeine and Redis managers are
            // configured to auto-create caches on first request.
            throw new IllegalStateException(
                "Cannot build layered cache '" + name + "' — l1=" + l1 + ", l2=" + l2);
        }
        return new LayeredCache(name, l1, l2);
    }

    @Override
    public Collection<String> getCacheNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(l1Manager.getCacheNames());
        names.addAll(l2Manager.getCacheNames());
        return names;
    }
}
