package com.contentworkflow.common.logging;

public final class WorkflowLogContextSnapshot {

    private static final WorkflowLogContextSnapshot EMPTY = new WorkflowLogContextSnapshot(null, null);

    private final String traceId;
    private final String requestId;

    private WorkflowLogContextSnapshot(String traceId, String requestId) {
        this.traceId = traceId;
        this.requestId = requestId;
    }

    public static WorkflowLogContextSnapshot empty() {
        return EMPTY;
    }

    public static WorkflowLogContextSnapshot of(String traceId, String requestId) {
        if ((traceId == null || traceId.isBlank()) && (requestId == null || requestId.isBlank())) {
            return EMPTY;
        }
        return new WorkflowLogContextSnapshot(traceId, requestId);
    }

    public String traceId() {
        return traceId;
    }

    public String requestId() {
        return requestId;
    }

    public boolean isEmpty() {
        return traceId == null && requestId == null;
    }
}
