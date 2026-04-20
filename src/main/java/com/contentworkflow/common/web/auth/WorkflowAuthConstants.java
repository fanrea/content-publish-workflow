package com.contentworkflow.common.web.auth;

/**
 * Shared constants for workflow authentication and request context.
 */
public final class WorkflowAuthConstants {

    private WorkflowAuthConstants() {
    }

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * Legacy headers kept only for compatibility tests and transition code.
     * Identity and roles must come from JWT, not from these raw headers.
     */
    public static final String ROLE_HEADER = "X-Workflow-Role";
    public static final String ROLES_HEADER = "X-Workflow-Roles";
    public static final String OPERATOR_ID_HEADER = "X-Workflow-Operator-Id";
    public static final String OPERATOR_NAME_HEADER = "X-Workflow-Operator-Name";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final String REQUEST_ROLE_ATTR = "workflowRole";
    public static final String REQUEST_OPERATOR_ATTR = "workflowOperator";
    public static final String REQUEST_ROLES_ATTR = "workflowRoles";
    public static final String REQUEST_PERMISSIONS_ATTR = "workflowPermissions";
    public static final String REQUEST_REQUEST_ID_ATTR = "workflowRequestId";
    public static final String REQUEST_TRACE_ID_ATTR = "workflowTraceId";
}
