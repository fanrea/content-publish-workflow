package com.contentworkflow.common.web.auth;

/**
 * 当前模块中的核心类型，用于承载对应场景下的业务数据或处理能力。
 */
public final class WorkflowAuditContextHolder {

    private static final ThreadLocal<WorkflowAuditContext> HOLDER = new ThreadLocal<>();

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     */

    private WorkflowAuditContextHolder() {
    }

    /**
     * 处理 set 相关逻辑，并返回对应的执行结果。
     *
     * @param context 参数 context 对应的业务输入值
     */

    public static void set(WorkflowAuditContext context) {
        if (context == null) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(context);
    }

    /**
     * 根据输入条件获取对应的业务数据详情。
     *
     * @return 方法处理后的结果对象
     */

    public static WorkflowAuditContext get() {
        return HOLDER.get();
    }

    /**
     * 处理 clear 相关逻辑，并返回对应的执行结果。
     */

    public static void clear() {
        HOLDER.remove();
    }
}
