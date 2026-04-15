package com.contentworkflow.common.web.auth;

/**
 * Workflow auth headers and request attribute keys.
 *
 * <p>These are intentionally "custom headers" (not a full OAuth/JWT setup) to keep this service
 * self-contained while still exercising role-based authorization and operator auditing.</p>
 */
public final class WorkflowAuthConstants {

    private WorkflowAuthConstants() {
    }

    /**
     * Single role header. Example: {@code X-Workflow-Role: EDITOR}.
     */
    public static final String ROLE_HEADER = "X-Workflow-Role";

    /**
     * Multi-role header. Example: {@code X-Workflow-Roles: editor,reviewer}.
     *
     * <p>If both {@link #ROLE_HEADER} and this header are present, roles will be merged.</p>
     */
    public static final String ROLES_HEADER = "X-Workflow-Roles";

    /**
     * Operator id. Example: {@code X-Workflow-Operator-Id: 10001}.
     */
    public static final String OPERATOR_ID_HEADER = "X-Workflow-Operator-Id";

    /**
     * Operator display name. Example: {@code X-Workflow-Operator-Name: alice}.
     */
    public static final String OPERATOR_NAME_HEADER = "X-Workflow-Operator-Name";

    /**
     * Optional request id propagated from gateway or caller.
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * Optional trace id propagated from gateway or caller.
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * Request attribute for effective role (chosen from provided roles intersecting the requirement).
     */
    public static final String REQUEST_ROLE_ATTR = "workflowRole";

    /**
     * Request attribute for the authenticated operator identity.
     */
    public static final String REQUEST_OPERATOR_ATTR = "workflowOperator";

    /**
     * Request attribute for all claimed roles (after parsing and validation).
     */
    public static final String REQUEST_ROLES_ATTR = "workflowRoles";

    /**
     * Request attribute for effective permissions derived from claimed roles.
     */
    public static final String REQUEST_PERMISSIONS_ATTR = "workflowPermissions";

    /**
     * Request attribute for the propagated request id.
     */
    public static final String REQUEST_REQUEST_ID_ATTR = "workflowRequestId";

    /**
     * Request attribute for the effective audit trace id.
     */
    public static final String REQUEST_TRACE_ID_ATTR = "workflowTraceId";
}
