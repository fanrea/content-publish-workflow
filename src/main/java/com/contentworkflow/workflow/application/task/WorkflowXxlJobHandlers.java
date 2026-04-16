package com.contentworkflow.workflow.application.task;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 处理器组件，负责承接特定工作流节点、任务或调度场景的执行逻辑。
 */
@Component
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
public class WorkflowXxlJobHandlers {

    private final PublishTaskWorker publishTaskWorker;
    private final OutboxRelayWorker outboxRelayWorker;
    private final WorkflowReconciliationService reconciliationService;

    /**
     * 创建当前类型实例，并注入运行该组件所需的依赖或初始化参数。
     *
     * @param publishTaskWorker 参数 publishTaskWorker 对应的业务输入值
     * @param outboxRelayWorker 参数 outboxRelayWorker 对应的业务输入值
     * @param reconciliationService 参数 reconciliationService 对应的业务输入值
     */

    public WorkflowXxlJobHandlers(PublishTaskWorker publishTaskWorker,
                                  OutboxRelayWorker outboxRelayWorker,
                                  WorkflowReconciliationService reconciliationService) {
        this.publishTaskWorker = publishTaskWorker;
        this.outboxRelayWorker = outboxRelayWorker;
        this.reconciliationService = reconciliationService;
    }

    /**
     * 处理 workflow publish task poll job 相关逻辑，并返回对应的执行结果。
     *
     * @param param 参数 param 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @XxlJob("workflowPublishTaskPollJob")
    public ReturnT<String> workflowPublishTaskPollJob(String param) {
        XxlJobHelper.log("trigger workflowPublishTaskPollJob, param={}", param);
        publishTaskWorker.pollOnce();
        return ReturnT.ofSuccess();
    }

    /**
     * 处理 workflow outbox relay job 相关逻辑，并返回对应的执行结果。
     *
     * @param param 参数 param 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @XxlJob("workflowOutboxRelayJob")
    public ReturnT<String> workflowOutboxRelayJob(String param) {
        XxlJobHelper.log("trigger workflowOutboxRelayJob, param={}", param);
        outboxRelayWorker.pollOnce();
        return ReturnT.ofSuccess();
    }

    /**
     * 处理 workflow dead publish task scan job 相关逻辑，并返回对应的执行结果。
     *
     * @param param 参数 param 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @XxlJob("workflowDeadPublishTaskScanJob")
    public ReturnT<String> workflowDeadPublishTaskScanJob(String param) {
        int limit = parseLimit(param, 100);
        int created = reconciliationService.scanDeadPublishTasks(limit);
        XxlJobHelper.log("trigger workflowDeadPublishTaskScanJob, limit={}, created={}", limit, created);
        return ReturnT.ofSuccess();
    }

    /**
     * 处理 workflow dead outbox scan job 相关逻辑，并返回对应的执行结果。
     *
     * @param param 参数 param 对应的业务输入值
     * @return 方法处理后的结果对象
     */

    @XxlJob("workflowDeadOutboxScanJob")
    public ReturnT<String> workflowDeadOutboxScanJob(String param) {
        int limit = parseLimit(param, 100);
        int discovered = reconciliationService.scanDeadOutboxEvents(limit);
        XxlJobHelper.log("trigger workflowDeadOutboxScanJob, limit={}, discovered={}", limit, discovered);
        return ReturnT.ofSuccess();
    }

    /**
     * 处理 parse limit 相关逻辑，并返回对应的执行结果。
     *
     * @param param 参数 param 对应的业务输入值
     * @param defaultValue 参数 defaultValue 对应的业务输入值
     * @return 统计值或数量结果
     */

    private int parseLimit(String param, int defaultValue) {
        if (param == null || param.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(param.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
