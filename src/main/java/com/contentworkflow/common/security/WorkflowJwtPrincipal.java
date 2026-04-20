package com.contentworkflow.common.security;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record WorkflowJwtPrincipal(
        String operatorId,
        String operatorName,
        Set<WorkflowRole> roles
) {
    public WorkflowJwtPrincipal {
        roles = roles == null || roles.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
