package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record ContentDraftResponse(
        Long id,
        String bizNo,
        String title,
        String summary,
        String body,
        Integer draftVersion,
        Integer publishedVersion,
        WorkflowStatus status,
        Long currentSnapshotId,
        String lastReviewComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
