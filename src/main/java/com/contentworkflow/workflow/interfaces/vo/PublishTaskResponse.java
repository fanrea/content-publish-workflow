package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.PublishTaskStatus;
import com.contentworkflow.workflow.domain.enums.PublishTaskType;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

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

