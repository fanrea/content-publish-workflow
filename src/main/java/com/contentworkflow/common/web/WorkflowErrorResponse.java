package com.contentworkflow.common.web;

import com.contentworkflow.common.logging.WorkflowLogContext;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkflowErrorResponse(
        boolean success,
        String code,
        String message,
        String traceId,
        String requestId,
        String path,
        OffsetDateTime timestamp,
        List<ValidationViolation> violations
) {

    public static WorkflowErrorResponse of(String code, String message, HttpServletRequest request) {
        return of(code, message, request, List.of());
    }

    public static WorkflowErrorResponse of(String code,
                                           String message,
                                           HttpServletRequest request,
                                           List<ValidationViolation> violations) {
        return new WorkflowErrorResponse(
                false,
                code,
                message,
                WorkflowLogContext.traceId(request),
                WorkflowLogContext.requestId(request),
                request.getRequestURI(),
                OffsetDateTime.now(),
                List.copyOf(violations)
        );
    }

    public record ValidationViolation(String field, String message) {
    }
}
