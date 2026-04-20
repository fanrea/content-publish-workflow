package com.contentworkflow.testing;

import com.contentworkflow.common.logging.WorkflowLogContext;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public final class WorkflowMdcTestSupport {

    private WorkflowMdcTestSupport() {
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    public static void withLoggingContext(String traceId, String requestId, CheckedRunnable action) throws Exception {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        Map<String, String> merged = previous == null ? new HashMap<>() : new HashMap<>(previous);
        putOrRemove(merged, WorkflowLogContext.TRACE_ID_KEY, traceId);
        putOrRemove(merged, WorkflowLogContext.REQUEST_ID_KEY, requestId);
        apply(merged);
        try {
            action.run();
        } finally {
            apply(previous);
        }
    }

    public static void withLoggingContextFromHeaders(Map<String, ?> headers, CheckedRunnable action) throws Exception {
        String requestId = header(headers, WorkflowLogContext.REQUEST_ID_HEADER);
        String traceId = header(headers, WorkflowLogContext.TRACE_ID_HEADER);
        if (isBlank(traceId)) {
            traceId = header(headers, WorkflowLogContext.B3_TRACE_ID_HEADER);
        }
        if (isBlank(traceId)) {
            traceId = requestId;
        }
        withLoggingContext(traceId, requestId, action);
    }

    private static void putOrRemove(Map<String, String> context, String key, String value) {
        if (isBlank(value)) {
            context.remove(key);
            return;
        }
        context.put(key, value);
    }

    private static void apply(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }

    private static String header(Map<String, ?> headers, String name) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
