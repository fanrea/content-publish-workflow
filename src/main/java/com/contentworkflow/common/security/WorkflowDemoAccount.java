package com.contentworkflow.common.security;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record WorkflowDemoAccount(
        String username,
        String password,
        String operatorId,
        String operatorName,
        Set<WorkflowRole> roles,
        boolean enabled
) {
    public WorkflowDemoAccount {
        roles = roles == null || roles.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
