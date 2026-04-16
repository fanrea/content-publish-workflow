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
 * 拦截器组件，用于在请求处理链路中执行鉴权、上下文注入和审计控制。
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

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param operatorResolver 参数 operatorResolver 对应的业务输入值
     * @param permissionPolicy 参数 permissionPolicy 对应的业务输入值
     */

    public WorkflowAuthorizationInterceptor(WorkflowOperatorResolver operatorResolver,
                                            WorkflowPermissionPolicy permissionPolicy) {
        this.operatorResolver = operatorResolver;
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * 处理 pre handle 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     * @param response 响应对象
     * @param handler 当前处理器对象
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

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

    /**
     * 处理 after completion 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     * @param response 响应对象
     * @param handler 当前处理器对象
     * @param ex 异常对象
     */

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        WorkflowAuditContextHolder.clear();
    }

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param handlerMethod 参数 handlerMethod 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param handlerMethod 参数 handlerMethod 对应的业务输入值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param value 待处理的原始值
     * @return 方法处理后的结果对象
     */

    private String normalizeHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
