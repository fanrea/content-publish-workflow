package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.time.LocalDateTime;

/**
 * Recoverable publish task view for operations.
 */
public record RecoverablePublishTaskResponse(
        Long id,
        Integer publishedVersion,
        PublishTaskType taskType,
        PublishTaskStatus status,
        Integer retryTimes,
        String errorMessage,
        LocalDateTime nextRunAt,
        String lockedBy,
        LocalDateTime lockedAt,
        boolean actionable,
        boolean staleVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
