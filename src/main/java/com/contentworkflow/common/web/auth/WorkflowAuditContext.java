package com.contentworkflow.common.web.auth;

/**
 * Request-scoped audit correlation context.
 */
public record WorkflowAuditContext(
        String traceId,
        String requestId
) {
}
