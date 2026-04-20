package com.contentworkflow.common.logging;

import com.contentworkflow.testing.WorkflowMdcTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowLogContextHeaderResolutionTest {

    @Test
    void appendHeaders_shouldAlignWithSharedTraceHeaderPriority() throws Exception {
        Map<String, Object> headers = Map.of(
                WorkflowLogContext.TRACE_PARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                WorkflowLogContext.REQUEST_ID_HEADER, "req-shared-1001"
        );

        Map<String, Object> resolved = WorkflowLogContext.appendHeaders(
                headers,
                "trace-seed",
                "request-seed"
        );

        assertThat(resolved)
                .containsEntry(WorkflowLogContext.TRACE_ID_HEADER, "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry(WorkflowLogContext.TRACE_ID_KEY, "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry(WorkflowLogContext.REQUEST_ID_HEADER, "req-shared-1001")
                .containsEntry(WorkflowLogContext.REQUEST_ID_KEY, "req-shared-1001");
    }

    @Test
    void appendHeaders_shouldFallbackToCurrentMdcBeforeStableSeed() throws Exception {
        WorkflowMdcTestSupport.withLoggingContext("trace-mdc-1002", "req-mdc-1002", () -> {
            Map<String, Object> resolved = WorkflowLogContext.appendHeaders(
                    Map.of("ignored", "value"),
                    "trace-seed",
                    "request-seed"
            );

            assertThat(resolved)
                    .containsEntry(WorkflowLogContext.TRACE_ID_HEADER, "trace-mdc-1002")
                    .containsEntry(WorkflowLogContext.REQUEST_ID_HEADER, "req-mdc-1002")
                    .containsEntry(WorkflowLogContext.TRACE_ID_KEY, "trace-mdc-1002")
                    .containsEntry(WorkflowLogContext.REQUEST_ID_KEY, "req-mdc-1002");
        });
    }
}
