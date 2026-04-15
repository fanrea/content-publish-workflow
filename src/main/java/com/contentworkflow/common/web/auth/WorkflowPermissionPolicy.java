package com.contentworkflow.common.web.auth;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role -> permission policy for the workflow service.
 */
@Component
public class WorkflowPermissionPolicy {

    private final Map<WorkflowRole, EnumSet<WorkflowPermission>> rolePermissions;

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

    public boolean hasPermissions(Set<WorkflowRole> roles, Set<WorkflowPermission> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        EnumSet<WorkflowPermission> actual = permissionsOf(roles);
        return actual.containsAll(requiredPermissions);
    }

    public boolean roleHasPermissions(WorkflowRole role, Set<WorkflowPermission> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return true;
        }
        return rolePermissions.getOrDefault(role, EnumSet.noneOf(WorkflowPermission.class))
                .containsAll(requiredPermissions);
    }
}
