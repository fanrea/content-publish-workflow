package com.contentworkflow.common.scheduler;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingXxlJobHandler extends IJobHandler {

    private static final Logger log = LoggerFactory.getLogger(TracingXxlJobHandler.class);

    private final String jobName;
    private final IJobHandler delegate;

    public TracingXxlJobHandler(String jobName, IJobHandler delegate) {
        this.jobName = jobName;
        this.delegate = delegate;
    }

    @Override
    public void execute() throws Exception {
        WorkflowSchedulerTraceContext.runXxl(jobName, () -> {
            log.info(
                    "Execute XXL-Job handler jobName={}, jobId={}, traceId={}, requestId={}, shard={}/{}",
                    jobName,
                    XxlJobHelper.getJobId(),
                    WorkflowLogContext.currentTraceId(),
                    WorkflowLogContext.currentRequestId(),
                    XxlJobHelper.getShardIndex(),
                    XxlJobHelper.getShardTotal()
            );
            delegate.execute();
        });
    }

    @Override
    public void init() throws Exception {
        WorkflowSchedulerTraceContext.runScheduled(jobName + "-init", delegate::init);
    }

    @Override
    public void destroy() throws Exception {
        WorkflowSchedulerTraceContext.runScheduled(jobName + "-destroy", delegate::destroy);
    }

    @Override
    public String toString() {
        return "TracingXxlJobHandler{" +
                "jobName='" + jobName + '\'' +
                ", delegate=" + delegate +
                '}';
    }
}
