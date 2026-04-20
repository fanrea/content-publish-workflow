package com.contentworkflow.common.messaging;

import com.contentworkflow.common.logging.WorkflowLogContext;
import com.contentworkflow.common.logging.WorkflowLogScope;
import com.contentworkflow.testing.WorkflowMdcTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowMessagingTraceContextContractTest {

    @Test
    void enrichOutboundHeaders_shouldPreferTraceparentAndRequestHeader() {
        Map<String, Object> headers = Map.of(
                WorkflowLogContext.TRACE_PARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                WorkflowLogContext.REQUEST_ID_HEADER, "req-shared-1001"
        );

        Map<String, Object> resolved = WorkflowMessagingTraceContext.enrichOutboundHeaders(headers);

        assertThat(resolved)
                .containsEntry(WorkflowLogContext.TRACE_ID_HEADER, "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry(WorkflowLogContext.TRACE_ID_KEY, "4bf92f3577b34da6a3ce929d0e0e4736")
                .containsEntry(WorkflowLogContext.REQUEST_ID_HEADER, "req-shared-1001")
                .containsEntry(WorkflowLogContext.REQUEST_ID_KEY, "req-shared-1001");
    }

    @Test
    void enrichOutboundHeaders_shouldFallbackToCurrentMdcWhenHeadersDoNotProvideIds() throws Exception {
        WorkflowMdcTestSupport.withLoggingContext("trace-mdc-1002", "req-mdc-1002", () -> {
            Map<String, Object> resolved = WorkflowMessagingTraceContext.enrichOutboundHeaders(Map.of("ignored", "value"));

            assertThat(resolved)
                    .containsEntry(WorkflowLogContext.TRACE_ID_HEADER, "trace-mdc-1002")
                    .containsEntry(WorkflowLogContext.REQUEST_ID_HEADER, "req-mdc-1002")
                    .containsEntry(WorkflowLogContext.TRACE_ID_KEY, "trace-mdc-1002")
                    .containsEntry(WorkflowLogContext.REQUEST_ID_KEY, "req-mdc-1002");
        });
    }

    @Test
    void openInboundScope_shouldRestorePreviousMdcAfterHandlingMessage() throws Exception {
        WorkflowMdcTestSupport.withLoggingContext("trace-outer-1003", "req-outer-1003", () -> {
            try (WorkflowLogScope ignored = WorkflowMessagingTraceContext.openInboundScope(
                    Map.of(
                            WorkflowLogContext.TRACE_ID_HEADER, "trace-inner-1003",
                            WorkflowLogContext.REQUEST_ID_HEADER, "req-inner-1003"
                    ),
                    "fallback-req"
            )) {
                assertThat(WorkflowLogContext.currentTraceId()).isEqualTo("trace-inner-1003");
                assertThat(WorkflowLogContext.currentRequestId()).isEqualTo("req-inner-1003");
            }

            assertThat(WorkflowLogContext.currentTraceId()).isEqualTo("trace-outer-1003");
            assertThat(WorkflowLogContext.currentRequestId()).isEqualTo("req-outer-1003");
        });
    }
}
