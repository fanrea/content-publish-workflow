package com.contentworkflow.common.web.auth;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.util.Objects;

/**
 * Authenticated operator identity resolved from request headers.
 */
public record WorkflowOperatorIdentity(
        String operatorId,
        String operatorName,
        WorkflowRole role
) {
    public WorkflowOperatorIdentity {
        operatorId = normalize(operatorId, "operatorId");
        operatorName = normalize(operatorName, "operatorName");
        role = Objects.requireNonNull(role, "role");
    }

    public static WorkflowOperatorIdentity system() {
        return new WorkflowOperatorIdentity("system", "system", WorkflowRole.ADMIN);
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
