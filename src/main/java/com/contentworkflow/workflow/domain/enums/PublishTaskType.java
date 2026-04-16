package com.contentworkflow.workflow.domain.enums;

/**
 * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
 */

public enum PublishTaskType {
    REFRESH_SEARCH_INDEX,
    SYNC_DOWNSTREAM_READ_MODEL,
    SEND_PUBLISH_NOTIFICATION
}
