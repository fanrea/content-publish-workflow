package com.contentworkflow.common.messaging.outbox;

/**
 * Outbox 事件状态。
 */
public enum OutboxEventStatus {
    NEW,
    SENDING,
    SENT,
    FAILED,
    DEAD
}

