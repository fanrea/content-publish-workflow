package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.web.auth.WorkflowAuditContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContextHolder;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;

import java.time.LocalDateTime;

/**
 * Build publish log entries with a consistent audit context.
 */
public final class WorkflowAuditLogFactory {

    private WorkflowAuditLogFactory() {
    }

    public static PublishLogEntry.PublishLogEntryBuilder operatorAction(Long draftId,
                                                                        String actionType,
                                                                        WorkflowOperatorIdentity operator) {
        return base(draftId, actionType)
                .operatorId(operator == null ? "system" : operator.operatorId())
                .operatorName(operator == null ? "system" : operator.operatorName());
    }

    public static PublishLogEntry.PublishLogEntryBuilder systemAction(Long draftId,
                                                                      String actionType,
                                                                      String operatorId,
                                                                      String operatorName) {
        return base(draftId, actionType)
                .operatorId(normalize(operatorId, "system"))
                .operatorName(normalize(operatorName, normalize(operatorId, "system")));
    }

    public static PublishLogEntry.PublishLogEntryBuilder base(Long draftId, String actionType) {
        WorkflowAuditContext auditContext = WorkflowAuditContextHolder.get();
        return PublishLogEntry.builder()
                .draftId(draftId)
                .actionType(actionType)
                .traceId(auditContext == null ? null : auditContext.traceId())
                .requestId(auditContext == null ? null : auditContext.requestId())
                .createdAt(LocalDateTime.now())
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId)
                .result(WorkflowAuditResult.SUCCESS);
    }

    public static PublishLogEntry.PublishLogEntryBuilder publishAction(Long draftId,
                                                                       Integer publishedVersion,
                                                                       String actionType,
                                                                       WorkflowOperatorIdentity operator) {
        return operatorAction(draftId, actionType, operator)
                .traceId(publishTraceId(draftId, publishedVersion))
                .publishedVersion(publishedVersion)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId);
    }

    public static PublishLogEntry.PublishLogEntryBuilder publishSystemAction(Long draftId,
                                                                             Integer publishedVersion,
                                                                             String actionType,
                                                                             String operatorId,
                                                                             String operatorName) {
        return systemAction(draftId, actionType, operatorId, operatorName)
                .traceId(publishTraceId(draftId, publishedVersion))
                .publishedVersion(publishedVersion)
                .targetType(WorkflowAuditTargetType.CONTENT_DRAFT)
                .targetId(draftId);
    }

    public static PublishLogEntry.PublishLogEntryBuilder taskAction(PublishTask task,
                                                                    String actionType,
                                                                    String operatorId,
                                                                    String operatorName) {
        return base(task == null ? null : task.getDraftId(), actionType)
                .traceId(task == null ? null : publishTraceId(task.getDraftId(), task.getPublishedVersion()))
                .operatorId(normalize(operatorId, "system"))
                .operatorName(normalize(operatorName, normalize(operatorId, "system")))
                .targetType(WorkflowAuditTargetType.PUBLISH_TASK)
                .targetId(task == null ? null : task.getId())
                .taskId(task == null ? null : task.getId())
                .publishedVersion(task == null ? null : task.getPublishedVersion());
    }

    public static PublishLogEntry.PublishLogEntryBuilder outboxAction(Long draftId,
                                                                      Long outboxEventId,
                                                                      Integer publishedVersion,
                                                                      String actionType,
                                                                      String operatorId,
                                                                      String operatorName) {
        return base(draftId, actionType)
                .traceId(publishTraceId(draftId, publishedVersion))
                .operatorId(normalize(operatorId, "system"))
                .operatorName(normalize(operatorName, normalize(operatorId, "system")))
                .targetType(WorkflowAuditTargetType.OUTBOX_EVENT)
                .targetId(outboxEventId)
                .outboxEventId(outboxEventId)
                .publishedVersion(publishedVersion);
    }

    public static String publishTraceId(Long draftId, Integer publishedVersion) {
        if (draftId == null || publishedVersion == null) {
            return null;
        }
        return "publish:" + draftId + ":" + publishedVersion;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
