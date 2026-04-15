package com.contentworkflow.workflow.infrastructure.cache;

import com.contentworkflow.common.cache.CacheKeys;
import com.contentworkflow.common.cache.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 草稿缓存门面。
 *
 * <p>当前项目主要通过 Repository 上的 `@Cacheable/@CacheEvict` 透明接入缓存。
 * 这个门面类用于未来需要显式控制缓存的场景，例如批量预热、异步事件驱动失效，
 * 或者对高频接口做更细粒度的缓存封装。</p>
 */
@Component
public class DraftCacheFacade {

    private final CacheManager cacheManager;

    public DraftCacheFacade(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 按 draftId 获取草稿详情，并提供一个回源加载器。
     *
     * <p>缓存只做 best-effort；获取或写入失败时不抛异常。
     * 同时不建议缓存 null，避免把“短暂查不到”的状态放大。</p>
     */
    public <T> Optional<T> getDraftDetailById(Long draftId, Class<T> type, Supplier<Optional<T>> loader) {
        if (draftId == null) {
            return Optional.empty();
        }
        Cache cache = cacheManager.getCache(CacheNames.DRAFT_DETAIL_BY_ID);
        String key = CacheKeys.draftId(draftId);

        if (cache != null) {
            T cached = cache.get(key, type);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        Optional<T> loaded = loader == null ? Optional.empty() : loader.get();
        if (loaded != null && loaded.isPresent() && cache != null) {
            cache.put(key, loaded.get());
        }
        return loaded == null ? Optional.empty() : loaded;
    }

    /**
     * 草稿发生变更后，显式驱逐详情缓存。
     *
     * <p>正常情况下业务层不需要主动调用，因为 `Repository.save` 已经带注解驱逐。
     * 这里主要服务于异步事件驱动的缓存失效场景。</p>
     */
    public void evictDraftDetail(Long draftId, String bizNo) {
        Cache byId = cacheManager.getCache(CacheNames.DRAFT_DETAIL_BY_ID);
        if (byId != null && draftId != null) {
            byId.evict(CacheKeys.draftId(draftId));
        }
        Cache byBizNo = cacheManager.getCache(CacheNames.DRAFT_DETAIL_BY_BIZ_NO);
        if (byBizNo != null && bizNo != null && !bizNo.isBlank()) {
            byBizNo.evict(CacheKeys.draftBizNo(bizNo));
        }
    }

    /** 驱逐草稿状态计数缓存，通常用于状态变更后。 */
    public void evictDraftStatusCount() {
        Cache cache = cacheManager.getCache(CacheNames.DRAFT_STATUS_COUNT);
        if (cache != null) {
            cache.clear();
        }
    }
}
