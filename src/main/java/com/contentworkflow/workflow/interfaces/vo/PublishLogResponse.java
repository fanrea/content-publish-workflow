package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;

import java.time.LocalDateTime;

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
