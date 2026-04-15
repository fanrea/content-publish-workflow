package com.contentworkflow.workflow.application.task;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * XXL-Job handlers that delegate to the existing workflow workers.
 *
 * <p>Each trigger executes one polling round so the admin controls cadence while business retry
 * semantics stay in the worker layer.</p>
 */
@Component
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
public class WorkflowXxlJobHandlers {

    private final PublishTaskWorker publishTaskWorker;
    private final OutboxRelayWorker outboxRelayWorker;
    private final WorkflowReconciliationService reconciliationService;

    public WorkflowXxlJobHandlers(PublishTaskWorker publishTaskWorker,
                                  OutboxRelayWorker outboxRelayWorker,
                                  WorkflowReconciliationService reconciliationService) {
        this.publishTaskWorker = publishTaskWorker;
        this.outboxRelayWorker = outboxRelayWorker;
        this.reconciliationService = reconciliationService;
    }

    @XxlJob("workflowPublishTaskPollJob")
    public ReturnT<String> workflowPublishTaskPollJob(String param) {
        XxlJobHelper.log("trigger workflowPublishTaskPollJob, param={}", param);
        publishTaskWorker.pollOnce();
        return ReturnT.ofSuccess();
    }

    @XxlJob("workflowOutboxRelayJob")
    public ReturnT<String> workflowOutboxRelayJob(String param) {
        XxlJobHelper.log("trigger workflowOutboxRelayJob, param={}", param);
        outboxRelayWorker.pollOnce();
        return ReturnT.ofSuccess();
    }

    @XxlJob("workflowDeadPublishTaskScanJob")
    public ReturnT<String> workflowDeadPublishTaskScanJob(String param) {
        int limit = parseLimit(param, 100);
        int created = reconciliationService.scanDeadPublishTasks(limit);
        XxlJobHelper.log("trigger workflowDeadPublishTaskScanJob, limit={}, created={}", limit, created);
        return ReturnT.ofSuccess();
    }

    @XxlJob("workflowDeadOutboxScanJob")
    public ReturnT<String> workflowDeadOutboxScanJob(String param) {
        int limit = parseLimit(param, 100);
        int discovered = reconciliationService.scanDeadOutboxEvents(limit);
        XxlJobHelper.log("trigger workflowDeadOutboxScanJob, limit={}, discovered={}", limit, discovered);
        return ReturnT.ofSuccess();
    }

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
