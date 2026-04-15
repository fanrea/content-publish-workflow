package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.time.LocalDateTime;

public record PublishTaskResponse(
        Long id,
        Integer publishedVersion,
        PublishTaskType taskType,
        PublishTaskStatus status,
        Integer retryTimes,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

