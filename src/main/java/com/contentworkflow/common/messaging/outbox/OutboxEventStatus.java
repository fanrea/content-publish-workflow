package com.contentworkflow.common.messaging.outbox;

/**
 * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
 */
public enum OutboxEventStatus {
    NEW,
    SENDING,
    SENT,
    FAILED,
    DEAD
}

