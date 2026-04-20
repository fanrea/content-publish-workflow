package com.contentworkflow.workflow.interfaces.vo;

import com.contentworkflow.common.security.WorkflowLoginSession;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.time.Instant;
import java.util.Set;

public record WorkflowLoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        String operatorId,
        String operatorName,
        Set<WorkflowRole> roles
) {
    public static WorkflowLoginResponse from(WorkflowLoginSession session) {
        return new WorkflowLoginResponse(
                session.tokenType(),
                session.accessToken(),
                session.expiresAt(),
                session.operatorId(),
                session.operatorName(),
                session.roles()
        );
    }
}
