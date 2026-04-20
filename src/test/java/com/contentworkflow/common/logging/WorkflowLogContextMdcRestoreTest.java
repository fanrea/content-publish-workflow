package com.contentworkflow.common.logging;

import com.contentworkflow.testing.WorkflowMdcTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowLogContextMdcRestoreTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void withLoggingContext_shouldCaptureAndRestoreOuterMdcState() throws Exception {
        MDC.put(WorkflowLogContext.TRACE_ID_KEY, "trace-outer");
        MDC.put(WorkflowLogContext.REQUEST_ID_KEY, "req-outer");
        MDC.put("tenantId", "tenant-a");

        WorkflowMdcTestSupport.withLoggingContext("trace-inner", "req-inner", () -> {
            assertEquals("trace-inner", WorkflowLogContext.currentTraceId());
            assertEquals("req-inner", WorkflowLogContext.currentRequestId());
            assertEquals("tenant-a", MDC.get("tenantId"));

            WorkflowMdcTestSupport.withLoggingContext("trace-nested", "req-nested", () -> {
                assertEquals("trace-nested", WorkflowLogContext.currentTraceId());
                assertEquals("req-nested", WorkflowLogContext.currentRequestId());
                assertEquals("tenant-a", MDC.get("tenantId"));
            });

            assertEquals("trace-inner", WorkflowLogContext.currentTraceId());
            assertEquals("req-inner", WorkflowLogContext.currentRequestId());
            assertEquals("tenant-a", MDC.get("tenantId"));
        });

        assertEquals("trace-outer", WorkflowLogContext.currentTraceId());
        assertEquals("req-outer", WorkflowLogContext.currentRequestId());
        assertEquals("tenant-a", MDC.get("tenantId"));
    }

    @Test
    void withLoggingContextFromHeaders_shouldBackfillTraceIdFromRequestIdAndClearWhenDone() throws Exception {
        WorkflowMdcTestSupport.withLoggingContextFromHeaders(
                Map.of(WorkflowLogContext.REQUEST_ID_HEADER, "req-only-1001"),
                () -> {
                    assertEquals("req-only-1001", WorkflowLogContext.currentTraceId());
                    assertEquals("req-only-1001", WorkflowLogContext.currentRequestId());
                }
        );

        assertNull(WorkflowLogContext.currentTraceId());
        assertNull(WorkflowLogContext.currentRequestId());
    }

    @Test
    void openFromCarrier_shouldDecodeByteHeadersAndRestoreOuterMdcState() throws Exception {
        WorkflowMdcTestSupport.withLoggingContext("trace-outer-2001", "req-outer-2001", () -> {
            try (WorkflowLogScope ignored = WorkflowLogContext.open(
                    Map.of(
                            WorkflowLogContext.B3_TRACE_ID_HEADER, "trace-b3-2001".getBytes(StandardCharsets.UTF_8),
                            WorkflowLogContext.REQUEST_ID_HEADER, "req-inner-2001".getBytes(StandardCharsets.UTF_8)
                    ),
                    "fallback-trace",
                    "fallback-req"
            )) {
                assertEquals("trace-b3-2001", WorkflowLogContext.currentTraceId());
                assertEquals("req-inner-2001", WorkflowLogContext.currentRequestId());
            }

            assertEquals("trace-outer-2001", WorkflowLogContext.currentTraceId());
            assertEquals("req-outer-2001", WorkflowLogContext.currentRequestId());
        });
    }
}
