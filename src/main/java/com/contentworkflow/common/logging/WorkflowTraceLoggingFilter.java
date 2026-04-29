package com.contentworkflow.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * WorkflowTraceLoggingFilter 类，负责当前模块的业务实现。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowTraceLoggingFilter extends OncePerRequestFilter {

    /**
     * 常量 log：日志记录器，用于输出运行与异常诊断信息。
     */
    private static final Logger log = LoggerFactory.getLogger(WorkflowTraceLoggingFilter.class);

    private final Duration slowRequestThreshold;

    /**
     * 构造当前类型实例，并注入运行所需依赖。
     * @param slowRequestThreshold 参数 slowRequestThreshold。
     */
    public WorkflowTraceLoggingFilter(
            @Value("${workflow.logging.request.slow-threshold:2s}") Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }

    /**
     * 判断当前条件是否成立。
     * @param request 参数 request。
     * @return 条件成立返回 true，否则返回 false。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator");
    }

    /**
     * 处理 doFilterInternal 相关业务逻辑。
     * @param request 参数 request。
     * @param response 参数 response。
     * @param filterChain 参数 filterChain。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = WorkflowLogContext.resolveTraceId(request);
        String requestId = WorkflowLogContext.resolveRequestId(request);
        Instant start = Instant.now();

        try (WorkflowLogScope ignored = WorkflowLogContext.bind(request, traceId, requestId)) {
            response.setHeader(WorkflowLogContext.TRACE_ID_HEADER, traceId);
            response.setHeader(WorkflowLogContext.REQUEST_ID_HEADER, requestId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                logRequestSummary(request, response, Duration.between(start, Instant.now()));
            }
        }
    }

    /**
     * 处理 logRequestSummary 相关业务逻辑。
     * @param request 参数 request。
     * @param response 参数 response。
     * @param duration 参数 duration。
     */
    private void logRequestSummary(HttpServletRequest request, HttpServletResponse response, Duration duration) {
        int status = response.getStatus();
        long elapsedMs = duration.toMillis();
        String message = "{} {} -> {} ({} ms)";

        if (status >= 500) {
            log.error(message, request.getMethod(), request.getRequestURI(), status, elapsedMs);
            return;
        }
        if (elapsedMs >= slowRequestThreshold.toMillis()) {
            log.warn(message, request.getMethod(), request.getRequestURI(), status, elapsedMs);
            return;
        }
        log.info(message, request.getMethod(), request.getRequestURI(), status, elapsedMs);
    }
}
