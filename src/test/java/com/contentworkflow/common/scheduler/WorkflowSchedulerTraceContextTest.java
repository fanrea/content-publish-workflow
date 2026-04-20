package com.contentworkflow.common.scheduler;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowSchedulerTraceContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void runScheduled_shouldPreserveOuterIdsAndRestoreAdditionalSchedulerTags() throws Exception {
        WorkflowMdcTestSupport.withLoggingContext("trace-scheduler-1001", "req-scheduler-1001", () -> {
            MDC.put("tenantId", "tenant-a");

            WorkflowSchedulerTraceContext.runScheduled("publish-poller", () -> {
                assertEquals("trace-scheduler-1001", WorkflowLogContext.currentTraceId());
                assertEquals("req-scheduler-1001", WorkflowLogContext.currentRequestId());
                assertEquals("scheduled", MDC.get("schedulerTriggerType"));
                assertEquals("publish-poller", MDC.get("schedulerTriggerName"));
                assertEquals("tenant-a", MDC.get("tenantId"));
            });

            assertEquals("trace-scheduler-1001", WorkflowLogContext.currentTraceId());
            assertEquals("req-scheduler-1001", WorkflowLogContext.currentRequestId());
            assertEquals("tenant-a", MDC.get("tenantId"));
            assertNull(MDC.get("schedulerTriggerType"));
            assertNull(MDC.get("schedulerTriggerName"));
        });
    }
}
