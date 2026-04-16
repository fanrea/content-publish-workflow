package com.contentworkflow.workflow.application.store;

import com.contentworkflow.common.web.auth.WorkflowAuditContext;
import com.contentworkflow.common.web.auth.WorkflowAuditContextHolder;
import com.contentworkflow.common.web.auth.WorkflowOperatorIdentity;
import com.contentworkflow.workflow.domain.entity.PublishTask;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditResult;
import com.contentworkflow.workflow.domain.enums.WorkflowAuditTargetType;

import java.time.LocalDateTime;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
public final class WorkflowAuditLogFactory {

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private WorkflowAuditLogFactory() {
    }

    /**
     * 处理 operator action 相关逻辑，并返回对应的执行结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

    public static PublishLogEntry.PublishLogEntryBuilder operatorAction(Long draftId,
                                                                        String actionType,
                                                                        WorkflowOperatorIdentity operator) {
        return base(draftId, actionType)
                .operatorId(operator == null ? "system" : operator.operatorId())
                .operatorName(operator == null ? "system" : operator.operatorName());
    }

    /**
     * 处理 system action 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operatorId 相关业务对象的唯一标识
     * @param operatorName 参数 operatorName 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static PublishLogEntry.PublishLogEntryBuilder systemAction(Long draftId,
                                                                      String actionType,
                                                                      String operatorId,
                                                                      String operatorName) {
        return base(draftId, actionType)
                .operatorId(normalize(operatorId, "system"))
                .operatorName(normalize(operatorName, normalize(operatorId, "system")));
    }

    /**
     * 处理 base 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param actionType 参数 actionType 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。该方法会结合当前操作人信息参与鉴权、审计或流程控制。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operator 当前操作人身份信息
     * @return 方法处理后的结果对象
     */

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

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operatorId 相关业务对象的唯一标识
     * @param operatorName 参数 operatorName 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 处理 task action 相关逻辑，并返回对应的执行结果。
     *
     * @param task 任务对象
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operatorId 相关业务对象的唯一标识
     * @param operatorName 参数 operatorName 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 处理 outbox action 相关逻辑，并返回对应的执行结果。
     *
     * @param draftId 草稿唯一标识
     * @param outboxEventId 相关业务对象的唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @param actionType 参数 actionType 对应的业务输入值
     * @param operatorId 相关业务对象的唯一标识
     * @param operatorName 参数 operatorName 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 触发发布流程，并返回发布动作对应的处理结果。
     *
     * @param draftId 草稿唯一标识
     * @param publishedVersion 参数 publishedVersion 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    public static String publishTraceId(Long draftId, Integer publishedVersion) {
        if (draftId == null || publishedVersion == null) {
            return null;
        }
        return "publish:" + draftId + ":" + publishedVersion;
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param value 待处理的原始值
     * @param fallback 参数 fallback 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
