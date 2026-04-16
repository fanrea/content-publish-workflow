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
 * 解析器组件，用于把输入信息转换为业务流程可直接使用的结构化结果。
 */
@Component
public class WorkflowOperatorResolver {

    private final WorkflowPermissionPolicy permissionPolicy;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param permissionPolicy 参数 permissionPolicy 对应的业务输入值
     */

    public WorkflowOperatorResolver(WorkflowPermissionPolicy permissionPolicy) {
        this.permissionPolicy = permissionPolicy;
    }

    /**
     * 解析输入信息并生成当前流程所需的结构化结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
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
     * 处理 pick effective role 相关逻辑，并返回对应的执行结果。
     *
     * @param roles 角色集合
     * @return 方法处理后的结果对象
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

    /**
     * 处理 parse roles required 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     * @return 方法处理后的结果对象
     */

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

    /**
     * 处理 parse roles value 相关逻辑，并返回对应的执行结果。
     *
     * @param value 待处理的原始值
     * @return 方法处理后的结果对象
     */

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

    /**
     * 处理 parse role token 相关逻辑，并返回对应的执行结果。
     *
     * @param token 参数 token 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private WorkflowRole parseRoleToken(String token) {
        try {
            return WorkflowRole.valueOf(token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("FORBIDDEN", "invalid workflow role: " + token);
        }
    }

    /**
     * 处理 required header 相关逻辑，并返回对应的执行结果。
     *
     * @param request 封装业务输入的请求对象
     * @param headerName 参数 headerName 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private String requiredHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value == null || value.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "missing required header: " + headerName);
        }
        return value.trim();
    }

    /**
     * 不可变数据模型，用于以紧凑形式承载当前场景下需要传递的数据内容。
     */

    public record ResolvedWorkflowAuth(
            WorkflowOperatorIdentity identity,
            EnumSet<WorkflowRole> roles,
            EnumSet<WorkflowPermission> permissions
    ) {
    }
}
