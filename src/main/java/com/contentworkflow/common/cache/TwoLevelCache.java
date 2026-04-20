package com.contentworkflow.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

import java.util.Map;
import java.util.concurrent.Callable;

final class TwoLevelCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelCache.class);

    private final String name;
    private final Cache localCache;
    private final Cache remoteCache;

    TwoLevelCache(String name, Cache localCache, Cache remoteCache) {
        this.name = name;
        this.localCache = localCache;
        this.remoteCache = remoteCache;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return Map.of("local", localCache.getNativeCache(), "remote", remoteCache.getNativeCache());
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper localValue = safeGet(localCache, key);
        if (localValue != null && localValue.get() != null) {
            return localValue;
        }

        ValueWrapper remoteValue = safeGet(remoteCache, key);
        if (remoteValue != null && remoteValue.get() != null) {
            safePut(localCache, key, remoteValue.get());
            return remoteValue;
        }
        return null;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T localValue = safeGet(localCache, key, type);
        if (localValue != null) {
            return localValue;
        }

        T remoteValue = safeGet(remoteCache, key, type);
        if (remoteValue != null) {
            safePut(localCache, key, remoteValue);
        }
        return remoteValue;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        T localValue = safeGetCallable(localCache, key);
        if (localValue != null) {
            return localValue;
        }

        T remoteValue = safeGetCallable(remoteCache, key);
        if (remoteValue != null) {
            safePut(localCache, key, remoteValue);
            return remoteValue;
        }

        try {
            T loaded = valueLoader.call();
            if (loaded != null) {
                safePut(remoteCache, key, loaded);
                safePut(localCache, key, loaded);
            }
            return loaded;
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
    }

    @Override
    public void put(Object key, Object value) {
        safePut(remoteCache, key, value);
        safePut(localCache, key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper remoteExisting = safePutIfAbsent(remoteCache, key, value);
        if (remoteExisting != null && remoteExisting.get() != null) {
            safePut(localCache, key, remoteExisting.get());
            return remoteExisting;
        }
        safePut(localCache, key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        safeEvict(localCache, key);
        safeEvict(remoteCache, key);
    }

    @Override
    public void clear() {
        safeClear(localCache);
        safeClear(remoteCache);
    }

    private ValueWrapper safeGet(Cache cache, Object key) {
        try {
            return cache.get(key);
        } catch (RuntimeException ex) {
            log.warn("cache GET failed. cacheName={}, key={}", cache.getName(), key, ex);
            return null;
        }
    }

    private <T> T safeGet(Cache cache, Object key, Class<T> type) {
        try {
            return cache.get(key, type);
        } catch (RuntimeException ex) {
            log.warn("cache GET(type) failed. cacheName={}, key={}", cache.getName(), key, ex);
            return null;
        }
    }

    private <T> T safeGetCallable(Cache cache, Object key) {
        ValueWrapper wrapper = safeGet(cache, key);
        if (wrapper == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T value = (T) wrapper.get();
        return value;
    }

    private void safePut(Cache cache, Object key, Object value) {
        try {
            cache.put(key, value);
        } catch (RuntimeException ex) {
            log.warn("cache PUT failed. cacheName={}, key={}", cache.getName(), key, ex);
        }
    }

    private ValueWrapper safePutIfAbsent(Cache cache, Object key, Object value) {
        try {
            return cache.putIfAbsent(key, value);
        } catch (RuntimeException ex) {
            log.warn("cache PUT_IF_ABSENT failed. cacheName={}, key={}", cache.getName(), key, ex);
            return null;
        }
    }

    private void safeEvict(Cache cache, Object key) {
        try {
            cache.evict(key);
        } catch (RuntimeException ex) {
            log.warn("cache EVICT failed. cacheName={}, key={}", cache.getName(), key, ex);
        }
    }

    private void safeClear(Cache cache) {
        try {
            cache.clear();
        } catch (RuntimeException ex) {
            log.warn("cache CLEAR failed. cacheName={}", cache.getName(), ex);
        }
    }
}
