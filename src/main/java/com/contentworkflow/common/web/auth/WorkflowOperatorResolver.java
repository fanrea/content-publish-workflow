package com.contentworkflow.common.web.auth;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parse operator identity and roles from custom headers.
 *
 * <p>设计目标：</p>
 * <ul>
 *     <li>接口层明确角色约束，拦截器负责鉴权</li>
 *     <li>操作人通过自定义 Header 传入，便于审计/回放</li>
 *     <li>兼容单角色 {@code X-Workflow-Role} 与多角色 {@code X-Workflow-Roles}</li>
 * </ul>
 */
@Component
public class WorkflowOperatorResolver {

    private final WorkflowPermissionPolicy permissionPolicy;

    public WorkflowOperatorResolver(WorkflowPermissionPolicy permissionPolicy) {
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * Resolve and validate operator + roles from headers. Throws {@code BusinessException} when invalid.
     */
    public ResolvedWorkflowAuth resolveRequired(HttpServletRequest request) {
        String operatorId = requiredHeader(request, WorkflowAuthConstants.OPERATOR_ID_HEADER);
        String operatorName = requiredHeader(request, WorkflowAuthConstants.OPERATOR_NAME_HEADER);
        EnumSet<WorkflowRole> roles = parseRolesRequired(request);
        EnumSet<WorkflowPermission> permissions = permissionPolicy.permissionsOf(roles);

        // Default effective role is the "strongest" claimed role. Interceptor may narrow it further.
        WorkflowRole effective = pickEffectiveRole(roles);
        WorkflowOperatorIdentity identity = new WorkflowOperatorIdentity(operatorId, operatorName, effective);
        return new ResolvedWorkflowAuth(identity, roles, permissions);
    }

    /**
     * Pick the effective role from a set. Higher privilege wins.
     *
     * <p>这里不做权限授予，仅用于选择一个“代表角色”写入审计/传递给 service。</p>
     */
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

    private EnumSet<WorkflowRole> parseRolesRequired(HttpServletRequest request) {
        String rolesHeader = request.getHeader(WorkflowAuthConstants.ROLES_HEADER);
        String roleHeader = request.getHeader(WorkflowAuthConstants.ROLE_HEADER);

        EnumSet<WorkflowRole> roles = EnumSet.noneOf(WorkflowRole.class);
        if (rolesHeader != null && !rolesHeader.isBlank()) {
            roles.addAll(parseRolesValue(rolesHeader));
        }
        if (roleHeader != null && !roleHeader.isBlank()) {
            roles.add(parseRoleToken(roleHeader));
        }
        if (roles.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "missing required header: "
                    + WorkflowAuthConstants.ROLE_HEADER + " or " + WorkflowAuthConstants.ROLES_HEADER);
        }
        return roles;
    }

    private EnumSet<WorkflowRole> parseRolesValue(String value) {
        // Split by common separators: comma / semicolon / whitespace.
        String[] tokens = value.split("[,;\\s]+");
        EnumSet<WorkflowRole> roles = EnumSet.noneOf(WorkflowRole.class);
        Arrays.stream(tokens)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .forEach(t -> roles.add(parseRoleToken(t)));
        if (roles.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "missing workflow role(s)");
        }
        return roles;
    }

    private WorkflowRole parseRoleToken(String token) {
        try {
            return WorkflowRole.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("FORBIDDEN", "invalid workflow role: " + token);
        }
    }

    private String requiredHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "missing required header: " + headerName);
        }
        return value.trim();
    }

    public record ResolvedWorkflowAuth(
            WorkflowOperatorIdentity identity,
            EnumSet<WorkflowRole> roles,
            EnumSet<WorkflowPermission> permissions
    ) {
    }
}
