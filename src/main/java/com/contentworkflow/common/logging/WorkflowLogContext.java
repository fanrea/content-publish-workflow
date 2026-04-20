package com.contentworkflow.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorkflowLogContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String B3_TRACE_ID_HEADER = "X-B3-TraceId";
    public static final String TRACE_PARENT_HEADER = "traceparent";
    public static final String TRACE_ID_ATTRIBUTE = WorkflowLogContext.class.getName() + ".TRACE_ID";
    public static final String REQUEST_ID_ATTRIBUTE = WorkflowLogContext.class.getName() + ".REQUEST_ID";

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$");
    private static final Pattern TRACE_PARENT_PATTERN =
            Pattern.compile("^[\\da-fA-F]{2}-([\\da-fA-F]{32})-[\\da-fA-F]{16}-[\\da-fA-F]{2}$");

    private WorkflowLogContext() {
    }

    public static String resolveTraceId(HttpServletRequest request) {
        return firstUsableId(
                request.getHeader(TRACE_ID_HEADER),
                request.getHeader(B3_TRACE_ID_HEADER),
                extractTraceParentTraceId(request.getHeader(TRACE_PARENT_HEADER)),
                currentTraceId()
        );
    }

    public static String resolveRequestId(HttpServletRequest request) {
        return firstUsableId(
                request.getHeader(REQUEST_ID_HEADER),
                currentRequestId()
        );
    }

    public static WorkflowLogContextSnapshot capture() {
        return WorkflowLogContextSnapshot.of(currentTraceId(), currentRequestId());
    }

    public static WorkflowLogContextSnapshot capture(HttpServletRequest request) {
        return WorkflowLogContextSnapshot.of(traceId(request), requestId(request));
    }

    public static WorkflowLogScope bind(HttpServletRequest request, String traceId, String requestId) {
        return bind(request, openSnapshot(traceId, requestId));
    }

    public static WorkflowLogScope bind(HttpServletRequest request, WorkflowLogContextSnapshot snapshot) {
        Objects.requireNonNull(request, "request must not be null");
        WorkflowLogContextSnapshot source = exact(snapshot);
        WorkflowLogContextSnapshot next = openSnapshot(source.traceId(), source.requestId());
        request.setAttribute(TRACE_ID_ATTRIBUTE, next.traceId());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, next.requestId());
        return restore(next);
    }

    public static WorkflowLogScope open(String traceId, String requestId) {
        return restore(openSnapshot(traceId, requestId));
    }

    public static WorkflowLogScope open(WorkflowLogContextSnapshot snapshot) {
        return restore(snapshot);
    }

    public static WorkflowLogScope open(Map<String, ?> headers,
                                        String traceFallbackCandidate,
                                        String requestFallbackCandidate) {
        return open(resolveCarrierSnapshot(headers, traceFallbackCandidate, requestFallbackCandidate));
    }

    public static WorkflowLogScope restore(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot previous = capture();
        restoreSnapshot(sanitizeSnapshot(snapshot));
        return new WorkflowLogScope(previous);
    }

    public static void clear() {
        restoreSnapshot(WorkflowLogContextSnapshot.empty());
    }

    public static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        return currentTraceId();
    }

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return currentRequestId();
    }

    public static Runnable wrap(Runnable task) {
        return wrap(capture(), task);
    }

    public static Runnable wrap(WorkflowLogContextSnapshot snapshot, Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                task.run();
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        return wrap(capture(), task);
    }

    public static <T> Callable<T> wrap(WorkflowLogContextSnapshot snapshot, Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                return task.call();
            }
        };
    }

    public static <T> Supplier<T> wrap(Supplier<T> task) {
        return wrap(capture(), task);
    }

    public static <T> Supplier<T> wrap(WorkflowLogContextSnapshot snapshot, Supplier<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                return task.get();
            }
        };
    }

    public static WorkflowLogContextSnapshot resolveSnapshot(String traceCandidate,
                                                             String requestCandidate,
                                                             String traceFallbackSeed,
                                                             String requestFallbackSeed) {
        return WorkflowLogContextSnapshot.of(
                firstUsableId(traceCandidate, currentTraceId(), stableId(traceFallbackSeed)),
                firstUsableId(requestCandidate, currentRequestId(), stableId(requestFallbackSeed))
        );
    }

    public static WorkflowLogContextSnapshot resolveSnapshot(Map<String, ?> headers,
                                                             String traceFallbackSeed,
                                                             String requestFallbackSeed) {
        return WorkflowLogContextSnapshot.of(
                firstUsableId(
                        headerValue(headers, TRACE_ID_HEADER),
                        headerValue(headers, B3_TRACE_ID_HEADER),
                        extractTraceParentTraceId(headerValue(headers, TRACE_PARENT_HEADER)),
                        headerValue(headers, TRACE_ID_KEY),
                        currentTraceId(),
                        stableId(traceFallbackSeed)
                ),
                firstUsableId(
                        headerValue(headers, REQUEST_ID_HEADER),
                        headerValue(headers, REQUEST_ID_KEY),
                        currentRequestId(),
                        stableId(requestFallbackSeed)
                )
        );
    }

    public static WorkflowLogContextSnapshot resolveCarrierSnapshot(Map<String, ?> headers,
                                                                    String traceFallbackCandidate,
                                                                    String requestFallbackCandidate) {
        return WorkflowLogContextSnapshot.of(
                firstUsableId(
                        headerValue(headers, TRACE_ID_HEADER),
                        headerValue(headers, B3_TRACE_ID_HEADER),
                        extractTraceParentTraceId(headerValue(headers, TRACE_PARENT_HEADER)),
                        headerValue(headers, TRACE_ID_KEY),
                        currentTraceId(),
                        traceFallbackCandidate
                ),
                firstUsableId(
                        headerValue(headers, REQUEST_ID_HEADER),
                        headerValue(headers, REQUEST_ID_KEY),
                        currentRequestId(),
                        requestFallbackCandidate
                )
        );
    }

    public static Map<String, Object> appendHeaders(Map<String, ?> headers,
                                                    String traceFallbackSeed,
                                                    String requestFallbackSeed) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        if (headers != null) {
            resolved.putAll(headers);
        }

        WorkflowLogContextSnapshot snapshot = resolveSnapshot(resolved, traceFallbackSeed, requestFallbackSeed);
        resolved.putIfAbsent(TRACE_ID_HEADER, snapshot.traceId());
        resolved.putIfAbsent(REQUEST_ID_HEADER, snapshot.requestId());
        resolved.putIfAbsent(TRACE_ID_KEY, snapshot.traceId());
        resolved.putIfAbsent(REQUEST_ID_KEY, snapshot.requestId());
        return Map.copyOf(resolved);
    }

    private static String firstUsableId(String... candidates) {
        for (String candidate : candidates) {
            String sanitized = sanitize(candidate);
            if (sanitized != null) {
                return sanitized;
            }
        }
        return generateId();
    }

    private static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (SAFE_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        return null;
    }

    private static String extractTraceParentTraceId(String traceParent) {
        return Optional.ofNullable(traceParent)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TRACE_PARENT_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .orElse(null);
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String stableId(String seed) {
        String resolvedSeed = seed == null ? UUID.randomUUID().toString() : seed;
        return UUID.nameUUIDFromBytes(resolvedSeed.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    static void restoreSnapshot(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot target = exact(snapshot);
        putOrRemove(TRACE_ID_KEY, target.traceId());
        putOrRemove(REQUEST_ID_KEY, target.requestId());
    }

    private static WorkflowLogContextSnapshot openSnapshot(String traceId, String requestId) {
        String resolvedTraceId = sanitize(traceId);
        if (resolvedTraceId == null) {
            resolvedTraceId = generateId();
        }
        String resolvedRequestId = sanitize(requestId);
        if (resolvedRequestId == null) {
            resolvedRequestId = generateId();
        }
        return WorkflowLogContextSnapshot.of(resolvedTraceId, resolvedRequestId);
    }

    private static WorkflowLogContextSnapshot sanitizeSnapshot(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot source = exact(snapshot);
        return WorkflowLogContextSnapshot.of(
                sanitize(source.traceId()),
                sanitize(source.requestId())
        );
    }

    private static WorkflowLogContextSnapshot exact(WorkflowLogContextSnapshot snapshot) {
        return snapshot == null ? WorkflowLogContextSnapshot.empty() : snapshot;
    }

    private static String headerValue(Map<String, ?> headers, String name) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}
