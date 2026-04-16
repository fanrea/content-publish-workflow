package com.contentworkflow.common.web.auth;

/**
 * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
 */
public enum WorkflowPermission {
    DRAFT_READ,
    DRAFT_DEBUG_READ,
    DRAFT_WRITE,
    DRAFT_STATS_READ,
    REVIEW_SUBMIT,
    REVIEW_DECIDE,
    PUBLISH_DIFF_READ,
    PUBLISH_EXECUTE,
    ROLLBACK_EXECUTE,
    OFFLINE_EXECUTE,
    TASK_VIEW,
    COMMAND_VIEW,
    LOG_VIEW,
    TASK_MANUAL_REQUEUE,
    OUTBOX_MANUAL_REQUEUE
}
