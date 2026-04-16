package com.contentworkflow.common.messaging;

import com.contentworkflow.common.cache.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Component
public class WorkflowMessageDeduplicationGuard {

    private final CacheManager cacheManager;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param cacheManager 参数 cacheManager 对应的业务输入值
     */

    public WorkflowMessageDeduplicationGuard(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 处理 first consume 相关逻辑，并返回对应的执行结果。
     *
     * @param messageId 相关业务对象的唯一标识
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    public boolean firstConsume(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }
        Cache cache = cacheManager.getCache(CacheNames.CONSUMED_WORKFLOW_MESSAGE);
        if (cache == null) {
            return true;
        }
        Cache.ValueWrapper existing = cache.putIfAbsent(messageId.trim(), LocalDateTime.now());
        return existing == null || Objects.isNull(existing.get());
    }
}
