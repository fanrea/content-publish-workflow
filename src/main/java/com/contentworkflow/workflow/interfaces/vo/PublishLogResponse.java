package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;

import java.time.LocalDateTime;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record PublishLogResponse(
        Long id,
        String traceId,
        String requestId,
        String actionType,
        String operatorId,
        String operatorName,
        WorkflowAuditTargetType targetType,
        Long targetId,
        Integer publishedVersion,
        Long taskId,
        Long outboxEventId,
        String beforeStatus,
        String afterStatus,
        WorkflowAuditResult result,
        String errorCode,
        String errorMessage,
        String remark,
        LocalDateTime createdAt
) {
}
