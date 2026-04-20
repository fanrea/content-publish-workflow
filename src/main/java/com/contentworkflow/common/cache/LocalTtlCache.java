package com.contentworkflow.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

final class LocalTtlCache implements Cache {

    private final String name;
    private final Duration ttl;
    private final int maximumSize;
    private final Map<Object, CacheEntry> store;

    LocalTtlCache(String name, WorkflowCacheSpec cacheSpec) {
        this.name = name;
        this.ttl = cacheSpec.ttl().isNegative() ? Duration.ZERO : cacheSpec.ttl();
        this.maximumSize = Math.max(1, Math.toIntExact(Math.min(Integer.MAX_VALUE, cacheSpec.maximumSize())));
        this.store = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, CacheEntry> eldest) {
                return size() > LocalTtlCache.this.maximumSize;
            }
        };
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return store;
    }

    @Override
    public ValueWrapper get(Object key) {
        synchronized (store) {
            CacheEntry entry = lookupEntry(key);
            return entry == null ? null : new SimpleValueWrapper(entry.value());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        synchronized (store) {
            CacheEntry entry = lookupEntry(key);
            if (entry == null) {
                return null;
            }
            Object value = entry.value();
            if (type != null && value != null && !type.isInstance(value)) {
                throw new IllegalStateException("cached value is not of required type " + type.getName());
            }
            return (T) value;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        synchronized (store) {
            CacheEntry entry = lookupEntry(key);
            if (entry != null) {
                return (T) entry.value();
            }
            try {
                T loaded = valueLoader.call();
                if (loaded != null) {
                    store.put(key, new CacheEntry(loaded, expiresAt()));
                }
                return loaded;
            } catch (Exception ex) {
                throw new ValueRetrievalException(key, valueLoader, ex);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            evict(key);
            return;
        }
        synchronized (store) {
            store.put(key, new CacheEntry(value, expiresAt()));
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        if (value == null) {
            return get(key);
        }
        synchronized (store) {
            CacheEntry existing = lookupEntry(key);
            if (existing != null) {
                return new SimpleValueWrapper(existing.value());
            }
            store.put(key, new CacheEntry(value, expiresAt()));
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        synchronized (store) {
            store.remove(key);
        }
    }

    @Override
    public void clear() {
        synchronized (store) {
            store.clear();
        }
    }

    private CacheEntry lookupEntry(Object key) {
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry;
    }

    private long expiresAt() {
        if (ttl.isZero()) {
            return Long.MAX_VALUE;
        }
        return System.nanoTime() + ttl.toNanos();
    }

    private record CacheEntry(Object value, long expiresAtNanos) {

        private boolean isExpired() {
            return expiresAtNanos != Long.MAX_VALUE && System.nanoTime() >= expiresAtNanos;
        }
    }
}
