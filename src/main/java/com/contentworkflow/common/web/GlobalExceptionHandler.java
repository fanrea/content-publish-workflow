package com.contentworkflow.common.web;

import com.contentworkflow.common.api.ApiResponse;
import com.contentworkflow.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 处理器组件，负责承接特定工作流节点、任务或调度场景的执行逻辑。
 */

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 handle business 相关逻辑，并返回对应的执行结果。
     *
     * @param exception 异常对象
     * @return 方法处理后的结果对象
     */

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "DRAFT_NOT_FOUND", "SNAPSHOT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_WORKFLOW_STATE" -> HttpStatus.CONFLICT;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.fail(exception.getCode(), exception.getMessage()));
    }

    /**
     * 处理 handle validation 相关逻辑，并返回对应的执行结果。
     *
     * @param exception 异常对象
     * @return 方法处理后的结果对象
     */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("invalid request");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    /**
     * 处理 handle constraint violation 相关逻辑，并返回对应的执行结果。
     *
     * @param exception 异常对象
     * @return 方法处理后的结果对象
     */

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION_ERROR", exception.getMessage()));
    }

    /**
     * 处理 handle unexpected 相关逻辑，并返回对应的执行结果。
     *
     * @param exception 异常对象
     * @return 方法处理后的结果对象
     */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR", exception.getMessage()));
    }
}
