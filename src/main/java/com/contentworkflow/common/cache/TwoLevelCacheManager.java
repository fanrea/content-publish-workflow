package com.contentworkflow.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class TwoLevelCacheManager implements CacheManager {

    private final CacheManager localCacheManager;
    private final CacheManager remoteCacheManager;
    private final Set<String> cacheNames;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    TwoLevelCacheManager(CacheManager localCacheManager,
                         CacheManager remoteCacheManager,
                         Map<String, WorkflowCacheSpec> cacheSpecs) {
        this.localCacheManager = localCacheManager;
        this.remoteCacheManager = remoteCacheManager;
        this.cacheNames = Collections.unmodifiableSet(new LinkedHashSet<>(cacheSpecs.keySet()));
    }

    @Override
    public Cache getCache(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return caches.computeIfAbsent(name, this::buildTwoLevelCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheNames;
    }

    private Cache buildTwoLevelCache(String name) {
        Cache local = localCacheManager.getCache(name);
        Cache remote = remoteCacheManager.getCache(name);
        if (local == null) {
            return remote;
        }
        if (remote == null) {
            return local;
        }
        return new TwoLevelCache(name, local, remote);
    }
}
