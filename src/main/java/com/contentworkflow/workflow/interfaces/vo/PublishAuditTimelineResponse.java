package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */

public record PublishAuditTimelineResponse(
        Long draftId,
        Integer publishedVersion,
        String traceId,
        String requestId,
        String initiatorId,
        String initiatorName,
        String currentDraftStatus,
        String finalActionType,
        String finalStatus,
        WorkflowAuditResult finalResult,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalEvents,
        List<PublishLogResponse> events
) {
}
