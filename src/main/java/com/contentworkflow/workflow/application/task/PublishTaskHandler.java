package com.contentworkflow.workflow.application.task;

import com.contentworkflow.workflow.domain.enums.PublishTaskType;

/**
 * 处理器组件，负责承接特定工作流节点、任务或调度场景的执行逻辑。
 */
public interface PublishTaskHandler {

    /**
     * 处理 task type 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    PublishTaskType taskType();

    /**
     * 处理 execute 相关逻辑，并返回对应的执行结果。
     *
     * @param ctx 参数 ctx 对应的业务输入值
     */

    void execute(PublishTaskContext ctx) throws Exception;

    /**
     * 处理 compensate 相关逻辑，并返回对应的执行结果。
     *
     * @param ctx 参数 ctx 对应的业务输入值
     */

    default void compensate(PublishTaskContext ctx) throws Exception {
        // No-op by default.
    }
}
