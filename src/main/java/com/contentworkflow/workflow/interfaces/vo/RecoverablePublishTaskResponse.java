package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
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
