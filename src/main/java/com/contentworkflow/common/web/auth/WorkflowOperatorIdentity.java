package com.contentworkflow.common.web.auth;

import com.contentworkflow.workflow.domain.enums.WorkflowRole;

import java.util.Objects;

/**
 * 持久化实体，用于映射数据库记录并承载 ORM 层的字段信息。
 */
public record WorkflowOperatorIdentity(
        String operatorId,
        String operatorName,
        WorkflowRole role
) {
    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    public WorkflowOperatorIdentity {
        operatorId = normalize(operatorId, "operatorId");
        operatorName = normalize(operatorName, "operatorName");
        role = Objects.requireNonNull(role, "role");
    }

    /**
     * 处理 system 相关逻辑，并返回对应的执行结果。
     *
     * @return 方法处理后的结果对象
     */

    public static WorkflowOperatorIdentity system() {
        return new WorkflowOperatorIdentity("system", "system", WorkflowRole.ADMIN);
    }

    /**
     * 对输入值进行标准化处理，便于后续统一使用。
     *
     * @param value 待处理的原始值
     * @param field 参数 field 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
