package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;

import java.time.LocalDateTime;
import java.util.List;

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
