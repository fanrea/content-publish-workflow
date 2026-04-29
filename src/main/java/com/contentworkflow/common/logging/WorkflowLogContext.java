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

/**
 * WorkflowLogContext 类，负责当前模块的业务实现。
 */
public final class WorkflowLogContext {

    /**
     * 常量 TRACE_ID_KEY：键名常量。
     */
    public static final String TRACE_ID_KEY = "traceId";
    /**
     * 常量 REQUEST_ID_KEY：键名常量。
     */
    public static final String REQUEST_ID_KEY = "requestId";
    /**
     * 常量 TRACE_ID_HEADER：业务常量。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /**
     * 常量 REQUEST_ID_HEADER：业务常量。
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    /**
     * 常量 B3_TRACE_ID_HEADER：业务常量。
     */
    public static final String B3_TRACE_ID_HEADER = "X-B3-TraceId";
    /**
     * 常量 TRACE_PARENT_HEADER：业务常量。
     */
    public static final String TRACE_PARENT_HEADER = "traceparent";
    /**
     * 常量 TRACE_ID_ATTRIBUTE：用于固定配置或标识值。
     */
    public static final String TRACE_ID_ATTRIBUTE = WorkflowLogContext.class.getName() + ".TRACE_ID";
    /**
     * 常量 REQUEST_ID_ATTRIBUTE：用于固定配置或标识值。
     */
    public static final String REQUEST_ID_ATTRIBUTE = WorkflowLogContext.class.getName() + ".REQUEST_ID";

    /**
     * 常量 SAFE_ID_PATTERN：用于固定配置或标识值。
     */
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$");
    /**
     * 常量 TRACE_PARENT_PATTERN：业务常量。
     */
    private static final Pattern TRACE_PARENT_PATTERN =
            Pattern.compile("^[\\da-fA-F]{2}-([\\da-fA-F]{32})-[\\da-fA-F]{16}-[\\da-fA-F]{2}$");

    /**
     * 构造当前类型实例，并注入运行所需依赖。
     */
    private WorkflowLogContext() {
    }

    /**
     * 解析并确定当前要使用的结果。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static String resolveTraceId(HttpServletRequest request) {
        return firstUsableId(
                request.getHeader(TRACE_ID_HEADER),
                request.getHeader(B3_TRACE_ID_HEADER),
                extractTraceParentTraceId(request.getHeader(TRACE_PARENT_HEADER)),
                currentTraceId()
        );
    }

    /**
     * 解析并确定当前要使用的结果。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static String resolveRequestId(HttpServletRequest request) {
        return firstUsableId(
                request.getHeader(REQUEST_ID_HEADER),
                currentRequestId()
        );
    }

    /**
     * 处理 capture 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogContextSnapshot capture() {
        return WorkflowLogContextSnapshot.of(currentTraceId(), currentRequestId());
    }

    /**
     * 处理 capture 相关业务逻辑。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogContextSnapshot capture(HttpServletRequest request) {
        return WorkflowLogContextSnapshot.of(traceId(request), requestId(request));
    }

    /**
     * 处理 bind 相关业务逻辑。
     * @param request 参数 request。
     * @param traceId 参数 traceId。
     * @param requestId 参数 requestId。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope bind(HttpServletRequest request, String traceId, String requestId) {
        return bind(request, openSnapshot(traceId, requestId));
    }

    /**
     * 处理 bind 相关业务逻辑。
     * @param request 参数 request。
     * @param snapshot 参数 snapshot。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope bind(HttpServletRequest request, WorkflowLogContextSnapshot snapshot) {
        Objects.requireNonNull(request, "request must not be null");
        WorkflowLogContextSnapshot source = exact(snapshot);
        WorkflowLogContextSnapshot next = openSnapshot(source.traceId(), source.requestId());
        request.setAttribute(TRACE_ID_ATTRIBUTE, next.traceId());
        request.setAttribute(REQUEST_ID_ATTRIBUTE, next.requestId());
        return restore(next);
    }

    /**
     * 处理 open 相关业务逻辑。
     * @param traceId 参数 traceId。
     * @param requestId 参数 requestId。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope open(String traceId, String requestId) {
        return restore(openSnapshot(traceId, requestId));
    }

    /**
     * 处理 open 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope open(WorkflowLogContextSnapshot snapshot) {
        return restore(snapshot);
    }

    /**
     * 处理 open 相关业务逻辑。
     * @param headers 参数 headers。
     * @param traceFallbackCandidate 参数 traceFallbackCandidate。
     * @param requestFallbackCandidate 参数 requestFallbackCandidate。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope open(Map<String, ?> headers,
                                        String traceFallbackCandidate,
                                        String requestFallbackCandidate) {
        return open(resolveCarrierSnapshot(headers, traceFallbackCandidate, requestFallbackCandidate));
    }

    /**
     * 处理 restore 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogScope restore(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot previous = capture();
        restoreSnapshot(sanitizeSnapshot(snapshot));
        return new WorkflowLogScope(previous);
    }

    /**
     * 处理 clear 相关业务逻辑。
     */
    public static void clear() {
        restoreSnapshot(WorkflowLogContextSnapshot.empty());
    }

    /**
     * 处理 currentTraceId 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public static String currentTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 处理 currentRequestId 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    /**
     * 处理 traceId 相关业务逻辑。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        return currentTraceId();
    }

    /**
     * 处理 requestId 相关业务逻辑。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (value instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return currentRequestId();
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static Runnable wrap(Runnable task) {
        return wrap(capture(), task);
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static Runnable wrap(WorkflowLogContextSnapshot snapshot, Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                task.run();
            }
        };
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        return wrap(capture(), task);
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static <T> Callable<T> wrap(WorkflowLogContextSnapshot snapshot, Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                return task.call();
            }
        };
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        return wrap(capture(), task);
    }

    /**
     * 处理 wrap 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @param task 参数 task。
     * @return 方法执行后的结果对象。
     */
    public static <T> Supplier<T> wrap(WorkflowLogContextSnapshot snapshot, Supplier<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        WorkflowLogContextSnapshot captured = exact(snapshot);
        return () -> {
            try (WorkflowLogScope ignored = restore(captured)) {
                return task.get();
            }
        };
    }

    /**
     * 解析并确定当前要使用的结果。
     * @param traceCandidate 参数 traceCandidate。
     * @param requestCandidate 参数 requestCandidate。
     * @param traceFallbackSeed 参数 traceFallbackSeed。
     * @param requestFallbackSeed 参数 requestFallbackSeed。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowLogContextSnapshot resolveSnapshot(String traceCandidate,
                                                             String requestCandidate,
                                                             String traceFallbackSeed,
                                                             String requestFallbackSeed) {
        return WorkflowLogContextSnapshot.of(
                firstUsableId(traceCandidate, currentTraceId(), stableId(traceFallbackSeed)),
                firstUsableId(requestCandidate, currentRequestId(), stableId(requestFallbackSeed))
        );
    }

    /**
     * 解析并确定当前要使用的结果。
     * @param headers 参数 headers。
     * @param traceFallbackSeed 参数 traceFallbackSeed。
     * @param requestFallbackSeed 参数 requestFallbackSeed。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 解析并确定当前要使用的结果。
     * @param headers 参数 headers。
     * @param traceFallbackCandidate 参数 traceFallbackCandidate。
     * @param requestFallbackCandidate 参数 requestFallbackCandidate。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 appendHeaders 相关业务逻辑。
     * @param headers 参数 headers。
     * @param traceFallbackSeed 参数 traceFallbackSeed。
     * @param requestFallbackSeed 参数 requestFallbackSeed。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 firstUsableId 相关业务逻辑。
     * @param candidates 参数 candidates。
     * @return 方法执行后的结果对象。
     */
    private static String firstUsableId(String... candidates) {
        for (String candidate : candidates) {
            String sanitized = sanitize(candidate);
            if (sanitized != null) {
                return sanitized;
            }
        }
        return generateId();
    }

    /**
     * 处理 sanitize 相关业务逻辑。
     * @param candidate 参数 candidate。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 extractTraceParentTraceId 相关业务逻辑。
     * @param traceParent 参数 traceParent。
     * @return 方法执行后的结果对象。
     */
    private static String extractTraceParentTraceId(String traceParent) {
        return Optional.ofNullable(traceParent)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(TRACE_PARENT_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .orElse(null);
    }

    /**
     * 处理 generateId 相关业务逻辑。
     * @return 方法执行后的结果对象。
     */
    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 处理 stableId 相关业务逻辑。
     * @param seed 参数 seed。
     * @return 方法执行后的结果对象。
     */
    private static String stableId(String seed) {
        String resolvedSeed = seed == null ? UUID.randomUUID().toString() : seed;
        return UUID.nameUUIDFromBytes(resolvedSeed.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    /**
     * 处理 restoreSnapshot 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     */
    static void restoreSnapshot(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot target = exact(snapshot);
        putOrRemove(TRACE_ID_KEY, target.traceId());
        putOrRemove(REQUEST_ID_KEY, target.requestId());
    }

    /**
     * 处理 openSnapshot 相关业务逻辑。
     * @param traceId 参数 traceId。
     * @param requestId 参数 requestId。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 sanitizeSnapshot 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @return 方法执行后的结果对象。
     */
    private static WorkflowLogContextSnapshot sanitizeSnapshot(WorkflowLogContextSnapshot snapshot) {
        WorkflowLogContextSnapshot source = exact(snapshot);
        return WorkflowLogContextSnapshot.of(
                sanitize(source.traceId()),
                sanitize(source.requestId())
        );
    }

    /**
     * 处理 exact 相关业务逻辑。
     * @param snapshot 参数 snapshot。
     * @return 方法执行后的结果对象。
     */
    private static WorkflowLogContextSnapshot exact(WorkflowLogContextSnapshot snapshot) {
        return snapshot == null ? WorkflowLogContextSnapshot.empty() : snapshot;
    }

    /**
     * 处理 headerValue 相关业务逻辑。
     * @param headers 参数 headers。
     * @param name 参数 name。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 putOrRemove 相关业务逻辑。
     * @param key 参数 key。
     * @param value 参数 value。
     */
    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}
