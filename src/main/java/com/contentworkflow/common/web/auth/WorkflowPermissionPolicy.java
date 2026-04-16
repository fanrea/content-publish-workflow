package com.contentworkflow.common.web.auth;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
@Component
public class WorkflowPermissionPolicy {

    private final Map<WorkflowRole, EnumSet<WorkflowPermission>> rolePermissions;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    public WorkflowPermissionPolicy() {
        EnumMap<WorkflowRole, EnumSet<WorkflowPermission>> mapping = new EnumMap<>(WorkflowRole.class);

        mapping.put(WorkflowRole.EDITOR, EnumSet.of(
                WorkflowPermission.DRAFT_READ,
                WorkflowPermission.DRAFT_WRITE,
                WorkflowPermission.DRAFT_STATS_READ,
                WorkflowPermission.REVIEW_SUBMIT,
                WorkflowPermission.PUBLISH_DIFF_READ
        ));

        mapping.put(WorkflowRole.REVIEWER, EnumSet.of(
                WorkflowPermission.DRAFT_READ,
                WorkflowPermission.DRAFT_STATS_READ,
                WorkflowPermission.REVIEW_DECIDE,
                WorkflowPermission.PUBLISH_DIFF_READ
        ));

        mapping.put(WorkflowRole.OPERATOR, EnumSet.of(
                WorkflowPermission.DRAFT_READ,
                WorkflowPermission.DRAFT_STATS_READ,
                WorkflowPermission.PUBLISH_DIFF_READ,
                WorkflowPermission.PUBLISH_EXECUTE,
                WorkflowPermission.ROLLBACK_EXECUTE,
                WorkflowPermission.OFFLINE_EXECUTE,
                WorkflowPermission.TASK_VIEW,
                WorkflowPermission.COMMAND_VIEW,
                WorkflowPermission.LOG_VIEW,
                WorkflowPermission.TASK_MANUAL_REQUEUE
        ));

        mapping.put(WorkflowRole.ADMIN, EnumSet.allOf(WorkflowPermission.class));
        this.rolePermissions = Collections.unmodifiableMap(mapping);
    }

    /**
     * 处理 permissions of 相关逻辑，并返回对应的执行结果。
     *
     * @param roles 角色集合
     * @return 方法处理后的结果对象
     */

    public EnumSet<WorkflowPermission> permissionsOf(Set<WorkflowRole> roles) {
        EnumSet<WorkflowPermission> result = EnumSet.noneOf(WorkflowPermission.class);
        if (roles == null) {
            return result;
        }
        for (WorkflowRole role : roles) {
            result.addAll(rolePermissions.getOrDefault(role, EnumSet.noneOf(WorkflowPermission.class)));
        }
        return result;
    }

    /**
     * 判断当前条件下是否满足指定约束或权限要求。
     *
     * @param roles 角色集合
     * @param requiredPermissions 参数 requiredPermissions 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    public boolean hasPermissions(Set<WorkflowRole> roles, Set<WorkflowPermission> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        EnumSet<WorkflowPermission> actual = permissionsOf(roles);
        return actual.containsAll(requiredPermissions);
    }

    /**
     * 处理 role has permissions 相关逻辑，并返回对应的执行结果。
     *
     * @param role 角色信息
     * @param requiredPermissions 参数 requiredPermissions 对应的业务输入值
     * @return 返回 true 表示条件成立或处理成功，返回 false 表示条件不成立或未命中
     */

    public boolean roleHasPermissions(WorkflowRole role, Set<WorkflowPermission> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        return rolePermissions.getOrDefault(role, EnumSet.noneOf(WorkflowPermission.class))
                .containsAll(requiredPermissions);
    }
}
