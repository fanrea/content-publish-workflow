package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

import java.time.LocalDateTime;

/**
 * 草稿列表页使用的轻量返回。
 *
 * <p>注意：不包含 body，避免列表页拉取大字段。</p>
 */
public record ContentDraftSummaryResponse(
        Long id,
        String bizNo,
        String title,
        String summary,
        Integer draftVersion,
        Integer publishedVersion,
        WorkflowStatus status,
        Long currentSnapshotId,
        String lastReviewComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        WorkflowActionResponse actions
) {
}

