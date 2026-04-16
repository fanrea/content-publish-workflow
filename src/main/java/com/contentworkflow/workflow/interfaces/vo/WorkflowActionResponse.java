package com.contentworkflow.workflow.interfaces.vo;

/**
 * 接口层响应模型，用于向调用方返回结构化的业务结果。
 */
public record WorkflowActionResponse(
        boolean canEdit,
        boolean canSubmitReview,
        boolean canReview,
        boolean canPublish,
        boolean canRollback,
        boolean canOffline
) {
}

