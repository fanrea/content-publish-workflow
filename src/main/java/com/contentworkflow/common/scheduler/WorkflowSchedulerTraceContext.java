package com.contentworkflow.common.scheduler;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.logging.WorkflowLogContextSnapshot;
import com.xxl.job.core.context.XxlJobHelper;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

final class WorkflowSchedulerTraceContext {

    private static final String STARTUP_TRACE_PREFIX = "sched-startup-";
    private static final String STARTUP_REQUEST_PREFIX = "sched-startup-req-";
    private static final String SCHEDULED_TRACE_PREFIX = "sched-";
    private static final String SCHEDULED_REQUEST_PREFIX = "sched-req-";
    private static final String XXL_TRACE_PREFIX = "xxl-";
    private static final String XXL_REQUEST_PREFIX = "xxl-req-";

    private WorkflowSchedulerTraceContext() {
    }

    static Scope openStartupScope(String source) {
        return openScope(
                currentOrGenerated(WorkflowLogContext.currentTraceId(), STARTUP_TRACE_PREFIX),
                currentOrGenerated(WorkflowLogContext.currentRequestId(), STARTUP_REQUEST_PREFIX),
                "startup",
                source
        );
    }

    static void runStartup(String source, CheckedRunnable action) throws Exception {
        try (Scope ignored = openStartupScope(source)) {
            action.run();
        }
    }

    static Scope openScheduledScope(String triggerName) {
        return openScope(
                currentOrGenerated(WorkflowLogContext.currentTraceId(), SCHEDULED_TRACE_PREFIX),
                currentOrGenerated(WorkflowLogContext.currentRequestId(), SCHEDULED_REQUEST_PREFIX),
                "scheduled",
                triggerName
        );
    }

    static Scope openXxlScope(String jobName) {
        String traceId = currentOrGenerated(WorkflowLogContext.currentTraceId(), XXL_TRACE_PREFIX);
        String requestId = currentOrGenerated(WorkflowLogContext.currentRequestId(), XXL_REQUEST_PREFIX);
        Scope scope = openScope(traceId, requestId, "xxl-job", jobName);
        XxlJobHelper.log(
                "trace attached for jobName={}, traceId={}, requestId={}, jobId={}, shard={}/{}",
                jobName,
                traceId,
                requestId,
                XxlJobHelper.getJobId(),
                XxlJobHelper.getShardIndex(),
                XxlJobHelper.getShardTotal()
        );
        return scope;
    }

    static void runScheduled(String triggerName, CheckedRunnable action) throws Exception {
        try (Scope ignored = openScheduledScope(triggerName)) {
            action.run();
        }
    }

    static void runXxl(String jobName, CheckedRunnable action) throws Exception {
        try (Scope ignored = openXxlScope(jobName)) {
            action.run();
        }
    }

    static Runnable wrapScheduled(String triggerName, Runnable task) {
        return () -> {
            try {
                runScheduled(triggerName, task::run);
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("scheduled task execution failed", ex);
            }
        };
    }

    static <T> Callable<T> wrapScheduled(String triggerName, Callable<T> task) {
        return () -> {
            try (Scope ignored = openScheduledScope(triggerName)) {
                return task.call();
            }
        };
    }

    private static Scope openScope(String traceId, String requestId, String triggerType, String triggerName) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        Map<String, String> current = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous);
        WorkflowLogContextSnapshot snapshot = WorkflowLogContext.resolveCarrierSnapshot(
                Map.of(),
                traceId,
                requestId
        );
        putOrRemove(current, WorkflowLogContext.TRACE_ID_KEY, snapshot.traceId());
        putOrRemove(current, WorkflowLogContext.REQUEST_ID_KEY, snapshot.requestId());
        current.put("schedulerTriggerType", triggerType);
        current.put("schedulerTriggerName", sanitizeName(triggerName));
        MDC.setContextMap(current);
        return new Scope(previous);
    }

    private static String currentOrGenerated(String current, String prefix) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.trim().replace(' ', '_');
    }

    private static void putOrRemove(Map<String, String> context, String key, String value) {
        if (value == null || value.isBlank()) {
            context.remove(key);
            return;
        }
        context.put(key, value);
    }

    @FunctionalInterface
    interface CheckedRunnable {
        void run() throws Exception;
    }

    static final class Scope implements AutoCloseable {
        private final Map<String, String> previous;

        private Scope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            MDC.clear();
            if (previous != null && !previous.isEmpty()) {
                MDC.setContextMap(previous);
            }
        }
    }
}
