package com.contentworkflow.common.cache;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

record WorkflowCacheSpec(Duration ttl, long maximumSize) {

    private static final long DEFAULT_MAXIMUM_SIZE = 512;

    static Map<String, WorkflowCacheSpec> builtin(WorkflowCacheProperties props, Duration defaultTtl) {
        Map<String, WorkflowCacheSpec> specs = new LinkedHashMap<>();
        specs.put(CacheNames.DRAFT_DETAIL_BY_ID, new WorkflowCacheSpec(props.getDraftDetailTtl(), 2_048));
        specs.put(CacheNames.DRAFT_DETAIL_BY_BIZ_NO, new WorkflowCacheSpec(props.getDraftDetailTtl(), 2_048));
        specs.put(CacheNames.DRAFT_LIST_LATEST, new WorkflowCacheSpec(props.getDraftListTtl(), 256));
        specs.put(CacheNames.DRAFT_STATUS_COUNT, new WorkflowCacheSpec(props.getDraftStatusCountTtl(), 256));
        specs.put(CacheNames.CONSUMED_WORKFLOW_MESSAGE, new WorkflowCacheSpec(defaultTtl, 20_000));
        specs.put(CacheNames.DEAD_OUTBOX_SCAN_MARKER, new WorkflowCacheSpec(defaultTtl, 4_096));
        return specs;
    }

    static WorkflowCacheSpec defaultSpec(Duration defaultTtl) {
        return new WorkflowCacheSpec(defaultTtl, DEFAULT_MAXIMUM_SIZE);
    }
}
