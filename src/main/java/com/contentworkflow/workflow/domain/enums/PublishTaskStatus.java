package com.contentworkflow.workflow.domain.enums;

/**
 * 枚举类型，用于集中定义当前领域中的固定状态、角色或分类值。
 */
public enum PublishTaskStatus {
    /**
     * 待执行。
     *
     * <p>任务已生成，但尚未被 worker 领取执行。</p>
     */
    PENDING,
    /**
     * 执行中。
     *
     * <p>任务已被某个 worker 领取。持久层可通过行锁/条件更新/分布式锁来避免重复领取。</p>
     */
    RUNNING,
    /**
     * 执行成功。
     */
    SUCCESS,
    /**
     * 执行失败（可重试）。
     */
    FAILED,
    /**
     * 死信状态：达到重试上限，不再自动重试。
     *
     * <p>需要人工介入或专门的补偿流程。</p>
     */
    DEAD
}

