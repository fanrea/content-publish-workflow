package com.contentworkflow.common.web.auth;

import com.contentworkflow.common.exception.BusinessException;
import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolve operator identity from headers and enforce role-based access on protected endpoints.
 *
 * <p>说明：</p>
 * <ul>
 *     <li>鉴权只发生在标注了 {@link RequireWorkflowRole} 的接口</li>
 *     <li>解析出的操作人会放在 request attribute 里，供 {@link CurrentWorkflowOperatorArgumentResolver} 注入</li>
 *     <li>支持单角色 {@code X-Workflow-Role} 与多角色 {@code X-Workflow-Roles}</li>
 * </ul>
 */
@Component
public class WorkflowAuthorizationInterceptor implements HandlerInterceptor {

    /**
     * Keep constants for tests/compat; prefer {@link WorkflowAuthConstants}.
     */
    public static final String ROLE_HEADER = WorkflowAuthConstants.ROLE_HEADER;
    public static final String OPERATOR_ID_HEADER = WorkflowAuthConstants.OPERATOR_ID_HEADER;
    public static final String OPERATOR_NAME_HEADER = WorkflowAuthConstants.OPERATOR_NAME_HEADER;
    public static final String REQUEST_ROLE_ATTR = WorkflowAuthConstants.REQUEST_ROLE_ATTR;
    public static final String REQUEST_OPERATOR_ATTR = WorkflowAuthConstants.REQUEST_OPERATOR_ATTR;

    private final WorkflowOperatorResolver operatorResolver;
    private final WorkflowPermissionPolicy permissionPolicy;

    public WorkflowAuthorizationInterceptor(WorkflowOperatorResolver operatorResolver,
                                            WorkflowPermissionPolicy permissionPolicy) {
        this.operatorResolver = operatorResolver;
        this.permissionPolicy = permissionPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireWorkflowPermission permissionRequirement = resolvePermissionRequirement(handlerMethod);
        RequireWorkflowRole roleRequirement = resolveRoleRequirement(handlerMethod);
        if (permissionRequirement == null && roleRequirement == null) {
            return true;
        }

        WorkflowOperatorResolver.ResolvedWorkflowAuth auth = operatorResolver.resolveRequired(request);
        EnumSet<WorkflowRole> matchedRoles = EnumSet.copyOf(auth.roles());

        if (permissionRequirement != null) {
            EnumSet<WorkflowPermission> requiredPermissions = EnumSet.copyOf(Arrays.asList(permissionRequirement.value()));
            if (!permissionPolicy.hasPermissions(auth.roles(), requiredPermissions)) {
                throw new BusinessException("FORBIDDEN", "workflow permission not allowed");
            }
            matchedRoles.removeIf(role -> !permissionPolicy.roleHasPermissions(role, requiredPermissions));
        }

        if (roleRequirement != null) {
            Set<WorkflowRole> allowedRoles = EnumSet.copyOf(Arrays.asList(roleRequirement.value()));
            matchedRoles.retainAll(allowedRoles);
            if (matchedRoles.isEmpty()) {
                throw new BusinessException("FORBIDDEN", "workflow role not allowed");
            }
        }

        if (matchedRoles.isEmpty()) {
            matchedRoles = EnumSet.copyOf(auth.roles());
        }

        // Choose an effective role for auditing and service downstream.
        WorkflowRole effectiveRole = operatorResolver.pickEffectiveRole(matchedRoles);
        WorkflowOperatorIdentity effectiveIdentity = new WorkflowOperatorIdentity(
                auth.identity().operatorId(),
                auth.identity().operatorName(),
                effectiveRole
        );

        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLE_ATTR, effectiveRole);
        request.setAttribute(WorkflowAuthConstants.REQUEST_ROLES_ATTR, auth.roles());
        request.setAttribute(WorkflowAuthConstants.REQUEST_PERMISSIONS_ATTR, auth.permissions());
        request.setAttribute(WorkflowAuthConstants.REQUEST_OPERATOR_ATTR, effectiveIdentity);
        String requestId = normalizeHeader(request.getHeader(WorkflowAuthConstants.REQUEST_ID_HEADER));
        String traceId = normalizeHeader(request.getHeader(WorkflowAuthConstants.TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }
        request.setAttribute(WorkflowAuthConstants.REQUEST_REQUEST_ID_ATTR, requestId);
        request.setAttribute(WorkflowAuthConstants.REQUEST_TRACE_ID_ATTR, traceId);
        WorkflowAuditContextHolder.set(new WorkflowAuditContext(traceId, requestId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        WorkflowAuditContextHolder.clear();
    }

    private RequireWorkflowPermission resolvePermissionRequirement(HandlerMethod handlerMethod) {
        RequireWorkflowPermission methodLevel = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireWorkflowPermission.class
        );
        if (methodLevel != null) {
            return methodLevel;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(),
                RequireWorkflowPermission.class
        );
    }

    private RequireWorkflowRole resolveRoleRequirement(HandlerMethod handlerMethod) {
        RequireWorkflowRole methodLevel = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequireWorkflowRole.class
        );
        if (methodLevel != null) {
            return methodLevel;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(),
                RequireWorkflowRole.class
        );
    }

    private String normalizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
