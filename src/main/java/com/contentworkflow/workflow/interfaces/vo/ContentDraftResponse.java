package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowStatus;

import java.time.LocalDateTime;

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
