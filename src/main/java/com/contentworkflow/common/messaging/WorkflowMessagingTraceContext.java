package com.contentworkflow.common.messaging;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.logging.WorkflowLogScope;

import java.util.Map;

public final class WorkflowMessagingTraceContext {

    private WorkflowMessagingTraceContext() {
    }

    public static Map<String, Object> enrichOutboundHeaders(Map<String, Object> headers) {
        return WorkflowLogContext.appendHeaders(
                headers,
                WorkflowLogContext.currentTraceId(),
                WorkflowLogContext.currentRequestId()
        );
    }

    public static WorkflowLogScope openInboundScope(Map<String, Object> headers, String fallbackRequestId) {
        return WorkflowLogContext.open(
                headers,
                WorkflowLogContext.currentTraceId(),
                fallbackRequestId
        );
    }
}
