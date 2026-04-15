package com.contentworkflow.workflow.interfaces.vo;

/**
 * 前端动作开关：前端可直接根据这些字段控制按钮显示/置灰，
 * 避免在前端重复实现状态机判断逻辑。
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

