package com.contentworkflow.workflow.domain.enums;

/**
 * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
 */

public enum WorkflowStatus {
    DRAFT,
    REVIEWING,
    REJECTED,
    APPROVED,
    PUBLISHING,
    PUBLISHED,
    PUBLISH_FAILED,
    OFFLINE
}
