package com.contentworkflow.workflow.infrastructure.cache;

import com.contentworkflow.common.cache.CacheKeys;
import com.contentworkflow.common.cache.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Component
public class DraftCacheFacade {

    private final CacheManager cacheManager;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param cacheManager 参数 cacheManager 对应的业务输入值
     */

    public DraftCacheFacade(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @param draftId 草稿唯一标识
     * @param type 参数 type 对应的业务输入值
     * @param loader 参数 loader 对应的业务输入值
     * @return 匹配到结果时返回对应对象，否则返回空的 Optional 容器
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
     * 处理 evict draft detail 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param bizNo 业务编号
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

    /**
     * 处理 evict draft status count 相关逻辑，并返回对应的执行结果。
     */
    public void evictDraftStatusCount() {
        Cache cache = cacheManager.getCache(CacheNames.DRAFT_STATUS_COUNT);
        if (cache != null) {
            cache.clear();
        }
    }
}
