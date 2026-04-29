package com.contentworkflow.common.web;

import com.contentworkflow.common.logging.WorkflowLogContext;
import jakarta.servlet.http.HttpServletRequest;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * WorkflowErrorResponse 响应对象，用于封装接口返回数据。
 */
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

    /**
     * 处理 of 相关业务逻辑。
     * @param code 参数 code。
     * @param message 参数 message。
     * @param request 参数 request。
     * @return 方法执行后的结果对象。
     */
    public static WorkflowErrorResponse of(String code, String message, HttpServletRequest request) {
        return of(code, message, request, List.of());
    }

    /**
     * 处理 of 相关业务逻辑。
     * @param code 参数 code。
     * @param message 参数 message。
     * @param request 参数 request。
     * @param violations 参数 violations。
     * @return 方法执行后的结果对象。
     */
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

    /**
     * 处理 ValidationViolation 相关业务逻辑。
     * @param field 参数 field。
     * @param message 参数 message。
     * @return 方法执行后的结果对象。
     */
    public record ValidationViolation(String field, String message) {
    }
}
