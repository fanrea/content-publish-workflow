package com.contentworkflow.workflow.interfaces.vo;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record DraftWorkflowSummaryResponse(
        ContentDraftSummaryResponse draft,
        long reviewCount,
        long snapshotCount,
        long publishTaskCount,
        long publishLogCount,
        Integer latestPublishedVersion,
        LocalDateTime latestPublishedAt
) {
}

