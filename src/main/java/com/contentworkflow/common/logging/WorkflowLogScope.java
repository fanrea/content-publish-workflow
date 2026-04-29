package com.contentworkflow.common.logging;

/**
 * WorkflowLogScope 类，负责当前模块的业务实现。
 */
public final class WorkflowLogScope implements AutoCloseable {

    private final WorkflowLogContextSnapshot previous;
    private boolean closed;

    /**
     * 构造当前类型实例，并注入运行所需依赖。
     * @param previous 参数 previous。
     */
    WorkflowLogScope(WorkflowLogContextSnapshot previous) {
        this.previous = previous;
    }

    /**
     * 处理 close 相关业务逻辑。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        WorkflowLogContext.restoreSnapshot(previous);
    }
}
