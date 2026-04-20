package com.contentworkflow.common.web.auth;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.common.security.WorkflowJwtPrincipal;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Resolve the authenticated workflow operator from Spring Security.
 */
@Component
public class WorkflowOperatorResolver {

    private final WorkflowPermissionPolicy permissionPolicy;

    public WorkflowOperatorResolver(WorkflowPermissionPolicy permissionPolicy) {
        this.permissionPolicy = permissionPolicy;
    }

    public ResolvedWorkflowAuth resolveRequired() {
        return resolveRequired(SecurityContextHolder.getContext().getAuthentication());
    }

    public ResolvedWorkflowAuth resolveRequired(Authentication authentication) {
        WorkflowJwtPrincipal principal = extractPrincipal(authentication);
        if (principal.roles().isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "missing workflow role(s)");
        }
        EnumSet<WorkflowRole> roles = EnumSet.copyOf(principal.roles());
        EnumSet<WorkflowPermission> permissions = permissionPolicy.permissionsOf(roles);

        WorkflowRole effective = pickEffectiveRole(roles);
        WorkflowOperatorIdentity identity = new WorkflowOperatorIdentity(
                principal.operatorId(),
                principal.operatorName(),
                effective
        );
        return new ResolvedWorkflowAuth(identity, roles, permissions);
    }

    public WorkflowRole pickEffectiveRole(Set<WorkflowRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "missing workflow role(s)");
        }
        if (roles.contains(WorkflowRole.ADMIN)) {
            return WorkflowRole.ADMIN;
        }
        if (roles.contains(WorkflowRole.OPERATOR)) {
            return WorkflowRole.OPERATOR;
        }
        if (roles.contains(WorkflowRole.REVIEWER)) {
            return WorkflowRole.REVIEWER;
        }
        return WorkflowRole.EDITOR;
    }

    private WorkflowJwtPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException("UNAUTHORIZED", "missing authenticated workflow operator");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof WorkflowJwtPrincipal workflowPrincipal) {
            return workflowPrincipal;
        }
        if (principal instanceof WorkflowOperatorIdentity identity) {
            return new WorkflowJwtPrincipal(
                    identity.operatorId(),
                    identity.operatorName(),
                    EnumSet.of(identity.role())
            );
        }

        throw new BusinessException("UNAUTHORIZED", "unsupported workflow principal");
    }

    public record ResolvedWorkflowAuth(
            WorkflowOperatorIdentity identity,
            EnumSet<WorkflowRole> roles,
            EnumSet<WorkflowPermission> permissions
    ) {
    }
}
