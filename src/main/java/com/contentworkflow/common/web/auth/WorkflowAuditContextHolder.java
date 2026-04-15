package com.contentworkflow.common.web.auth;

/**
 * Thread-local holder for workflow audit context.
 */
public final class WorkflowAuditContextHolder {

    private static final ThreadLocal<WorkflowAuditContext> HOLDER = new ThreadLocal<>();

    private WorkflowAuditContextHolder() {
    }

    public static void set(WorkflowAuditContext context) {
        if (context == null) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(context);
    }

    public static WorkflowAuditContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
