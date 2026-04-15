package com.contentworkflow.common.messaging;

import com.contentworkflow.common.cache.CacheNames;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Best-effort MQ message deduplication guard.
 */
@Component
public class WorkflowMessageDeduplicationGuard {

    private final CacheManager cacheManager;

    public WorkflowMessageDeduplicationGuard(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

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
