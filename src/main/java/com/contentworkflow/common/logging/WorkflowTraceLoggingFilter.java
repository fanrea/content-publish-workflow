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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowTraceLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTraceLoggingFilter.class);

    private final Duration slowRequestThreshold;

    public WorkflowTraceLoggingFilter(
            @Value("${workflow.logging.request.slow-threshold:2s}") Duration slowRequestThreshold) {
        this.slowRequestThreshold = slowRequestThreshold;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator");
    }

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
