package com.contentworkflow.workflow.infrastructure.cache;

import com.contentworkflow.common.cache.CacheKeys;
import com.contentworkflow.common.cache.CacheNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DraftCacheFacadeTest {

    private TwoLevelCacheManager cacheManager;
    private DraftCacheFacade facade;

    @BeforeEach
    void setUp() {
        cacheManager = new TwoLevelCacheManager(
                CacheNames.DRAFT_DETAIL_BY_ID,
                CacheNames.DRAFT_DETAIL_BY_BIZ_NO,
                CacheNames.DRAFT_STATUS_COUNT
        );
        facade = new DraftCacheFacade(cacheManager);
    }

    @Test
    void getDraftDetailById_shouldWarmLocalCacheFromRemoteHit() {
        TrackingTwoLevelCache cache = cacheManager.named(CacheNames.DRAFT_DETAIL_BY_ID);
        String key = CacheKeys.draftId(42L);
        cache.putRemote(key, "remote-draft");
        AtomicInteger loaderCalls = new AtomicInteger();

        Optional<String> draft = facade.getDraftDetailById(42L, String.class, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("db-draft");
        });

        assertThat(draft).contains("remote-draft");
        assertThat(loaderCalls).hasValue(0);
        assertThat(cache.localContains(key)).isTrue();
        assertThat(cache.remoteContains(key)).isTrue();
    }

    @Test
    void getDraftDetailById_shouldPopulateBothLevelsOnLoaderMiss() {
        TrackingTwoLevelCache cache = cacheManager.named(CacheNames.DRAFT_DETAIL_BY_ID);
        String key = CacheKeys.draftId(7L);
        AtomicInteger loaderCalls = new AtomicInteger();

        Optional<String> first = facade.getDraftDetailById(7L, String.class, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("draft-7");
        });
        Optional<String> second = facade.getDraftDetailById(7L, String.class, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of("draft-7-db");
        });

        assertThat(first).contains("draft-7");
        assertThat(second).contains("draft-7");
        assertThat(loaderCalls).hasValue(1);
        assertThat(cache.localContains(key)).isTrue();
        assertThat(cache.remoteContains(key)).isTrue();
    }

    @Test
    void evictMethods_shouldClearBothLevels() {
        TrackingTwoLevelCache byId = cacheManager.named(CacheNames.DRAFT_DETAIL_BY_ID);
        TrackingTwoLevelCache byBizNo = cacheManager.named(CacheNames.DRAFT_DETAIL_BY_BIZ_NO);
        TrackingTwoLevelCache count = cacheManager.named(CacheNames.DRAFT_STATUS_COUNT);
        String idKey = CacheKeys.draftId(9L);
        String bizKey = CacheKeys.draftBizNo("BIZ-009");

        byId.put(idKey, "detail");
        byBizNo.put(bizKey, "detail");
        count.put("stats", Map.of("draft", 1L));

        facade.evictDraftDetail(9L, "BIZ-009");
        facade.evictDraftStatusCount();

        assertThat(byId.localContains(idKey)).isFalse();
        assertThat(byId.remoteContains(idKey)).isFalse();
        assertThat(byBizNo.localContains(bizKey)).isFalse();
        assertThat(byBizNo.remoteContains(bizKey)).isFalse();
        assertThat(count.localEntries()).isEmpty();
        assertThat(count.remoteEntries()).isEmpty();
    }

    private static final class TwoLevelCacheManager implements CacheManager {

        private final Map<String, TrackingTwoLevelCache> caches = new ConcurrentHashMap<>();

        private TwoLevelCacheManager(String... cacheNames) {
            for (String cacheName : cacheNames) {
                caches.put(cacheName, new TrackingTwoLevelCache(cacheName));
            }
        }

        @Override
        public Cache getCache(String name) {
            return caches.computeIfAbsent(name, TrackingTwoLevelCache::new);
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }

        private TrackingTwoLevelCache named(String cacheName) {
            return caches.get(cacheName);
        }
    }

    private static final class TrackingTwoLevelCache implements Cache {

        private final String name;
        private final Map<Object, Object> local = new ConcurrentHashMap<>();
        private final Map<Object, Object> remote = new ConcurrentHashMap<>();

        private TrackingTwoLevelCache(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        public ValueWrapper get(Object key) {
            if (local.containsKey(key)) {
                return new SimpleValueWrapper(local.get(key));
            }
            if (remote.containsKey(key)) {
                Object value = remote.get(key);
                local.put(key, value);
                return new SimpleValueWrapper(value);
            }
            return null;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            if (wrapper == null || wrapper.get() == null) {
                return null;
            }
            return type.cast(wrapper.get());
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper wrapper = get(key);
            if (wrapper != null && wrapper.get() != null) {
                @SuppressWarnings("unchecked")
                T cached = (T) wrapper.get();
                return cached;
            }
            try {
                T loaded = valueLoader.call();
                put(key, loaded);
                return loaded;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void put(Object key, Object value) {
            local.put(key, value);
            remote.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            ValueWrapper existing = get(key);
            if (existing != null) {
                return existing;
            }
            put(key, value);
            return null;
        }

        @Override
        public void evict(Object key) {
            local.remove(key);
            remote.remove(key);
        }

        @Override
        public void clear() {
            local.clear();
            remote.clear();
        }

        private void putRemote(Object key, Object value) {
            remote.put(key, value);
        }

        private boolean localContains(Object key) {
            return local.containsKey(key);
        }

        private boolean remoteContains(Object key) {
            return remote.containsKey(key);
        }

        private Map<Object, Object> localEntries() {
            return local;
        }

        private Map<Object, Object> remoteEntries() {
            return remote;
        }
    }
}
