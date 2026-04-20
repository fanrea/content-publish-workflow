package com.contentworkflow.common.security;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record WorkflowLoginSession(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        String operatorId,
        String operatorName,
        Set<WorkflowRole> roles
) {
    public WorkflowLoginSession {
        roles = roles == null || roles.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
