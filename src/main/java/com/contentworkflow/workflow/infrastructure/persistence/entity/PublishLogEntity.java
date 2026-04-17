package com.contentworkflow.workflow.infrastructure.persistence.entity;

import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class PublishLogEntity {

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

    public void prepareForInsert() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (targetType == null) {
            targetType = WorkflowAuditTargetType.CONTENT_DRAFT;
        }
        if (result == null) {
            result = WorkflowAuditResult.SUCCESS;
        }
    }
}
