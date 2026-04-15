package com.contentworkflow.workflow.domain.enums;

/**
 * Structured audit result for workflow actions.
 */
public enum WorkflowAuditResult {
    SUCCESS,
    ACCEPTED,
    RETRYING,
    FAILED,
    INTERVENTION_REQUIRED
}
