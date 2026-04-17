package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record ContentDraftSummaryResponse(
        Long id,
        String bizNo,
        String title,
        String summary,
        Integer draftVersion,
        Integer publishedVersion,
        Long version,
        WorkflowStatus status,
        Long currentSnapshotId,
        String lastReviewComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        WorkflowActionResponse actions
) {
}
