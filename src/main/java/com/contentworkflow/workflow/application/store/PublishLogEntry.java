package com.contentworkflow.workflow.application.store;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PublishLogEntry {
    private Long id;
    private Long draftId;
    private String traceId;
    private String requestId;
    private String actionType;
    private String operatorId;
    private String operatorName;
    private WorkflowAuditTargetType targetType;
    private Long targetId;
    private Integer publishedVersion;
    private Long taskId;
    private Long outboxEventId;
    private String beforeStatus;
    private String afterStatus;
    private WorkflowAuditResult result;
    private String errorCode;
    private String errorMessage;
    private String remark;
    private LocalDateTime createdAt;
}
