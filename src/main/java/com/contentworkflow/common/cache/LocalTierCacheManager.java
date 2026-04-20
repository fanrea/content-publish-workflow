package com.contentworkflow.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LocalTierCacheManager implements CacheManager {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final Map<String, WorkflowCacheSpec> cacheSpecs;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final LocalTierCacheFactory cacheFactory = LocalTierCacheFactory.create();

    LocalTierCacheManager(Map<String, WorkflowCacheSpec> cacheSpecs) {
        this.cacheSpecs = new ConcurrentHashMap<>(cacheSpecs);
    }

    @Override
    public Cache getCache(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return caches.computeIfAbsent(name, cacheName -> cacheFactory.create(cacheName, resolveSpec(cacheName)));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheSpecs.keySet());
    }

    private WorkflowCacheSpec resolveSpec(String cacheName) {
        return cacheSpecs.getOrDefault(cacheName, WorkflowCacheSpec.defaultSpec(DEFAULT_TTL));
    }
}
