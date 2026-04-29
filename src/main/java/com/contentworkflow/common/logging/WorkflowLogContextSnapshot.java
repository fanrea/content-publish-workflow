package com.contentworkflow.common.logging;

/**
 * WorkflowLogContextSnapshot 类，负责当前模块的业务实现。
 */
public final class WorkflowLogContextSnapshot {

    /**
     * 字段 EMPTY：用于保存当前对象运行所需的状态数据。
     */
    private static final WorkflowLogContextSnapshot EMPTY = new WorkflowLogContextSnapshot(null, null);

    private final String traceId;
    private final String requestId;

    /**
     * 构造当前类型实例，并注入运行所需依赖。
     * @param traceId 参数 traceId。
     * @param requestId 参数 requestId。
     */
    private WorkflowLogContextSnapshot(String traceId, String requestId) {
        this.traceId = traceId;
        this.requestId = requestId;
    }

    /**
     * 处理 empty 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogContextSnapshot empty() {
        return EMPTY;
    }

    /**
     * 处理 of 相关业务逻辑。
     * @param traceId 参数 traceId。
     * @param requestId 参数 requestId。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogContextSnapshot of(String traceId, String requestId) {
        if ((traceId == null || traceId.isBlank()) && (requestId == null || requestId.isBlank())) {
            return EMPTY;
        }
        return new WorkflowLogContextSnapshot(traceId, requestId);
    }

    /**
     * 处理 traceId 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public String traceId() {
        return traceId;
    }

    /**
     * 处理 requestId 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public String requestId() {
        return requestId;
    }

    /**
     * 判断当前条件是否成立。
     * @return 条件成立返回 true，否则返回 false。
     */
    public boolean isEmpty() {
        return traceId == null && requestId == null;
    }
}
