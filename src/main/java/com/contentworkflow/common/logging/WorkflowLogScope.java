package com.contentworkflow.common.logging;

public final class WorkflowLogScope implements AutoCloseable {

    private final WorkflowLogContextSnapshot previous;
    private boolean closed;

    WorkflowLogScope(WorkflowLogContextSnapshot previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        WorkflowLogContext.restoreSnapshot(previous);
    }
}
