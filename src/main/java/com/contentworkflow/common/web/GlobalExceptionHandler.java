package com.contentworkflow.common.web;

import com.contentworkflow.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<WorkflowErrorResponse> handleBusiness(BusinessException exception,
                                                                HttpServletRequest request) {
        HttpStatus status = mapBusinessStatus(exception.getCode());
        logAtLevel(status, "business exception [{}] on {} {}", exception.getCode(), request.getMethod(), request.getRequestURI(), exception);
        return build(status, exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WorkflowErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                                  HttpServletRequest request) {
        List<WorkflowErrorResponse.ValidationViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new WorkflowErrorResponse.ValidationViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("request body validation failed on {} {}: {}", request.getMethod(), request.getRequestURI(), violations);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request validation failed", request, violations);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<WorkflowErrorResponse> handleBind(BindException exception,
                                                            HttpServletRequest request) {
        List<WorkflowErrorResponse.ValidationViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new WorkflowErrorResponse.ValidationViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        log.warn("request binding failed on {} {}: {}", request.getMethod(), request.getRequestURI(), violations);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request binding failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<WorkflowErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                           HttpServletRequest request) {
        List<WorkflowErrorResponse.ValidationViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(violation -> new WorkflowErrorResponse.ValidationViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        log.warn("request constraint violation on {} {}: {}", request.getMethod(), request.getRequestURI(), violations);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "request validation failed", request, violations);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<WorkflowErrorResponse> handleRequestErrors(Exception exception,
                                                                     HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        } else if (exception instanceof HttpRequestMethodNotSupportedException) {
            status = HttpStatus.METHOD_NOT_ALLOWED;
        }
        log.warn("request rejected on {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        return build(status, "INVALID_REQUEST", normalizeMessage(exception.getMessage(), "invalid request"), request);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<WorkflowErrorResponse> handleFrameworkError(ErrorResponseException exception,
                                                                      HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code = status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        String message = normalizeMessage(
                exception.getBody() != null ? exception.getBody().getDetail() : null,
                status.is4xxClientError() ? "request rejected" : "internal server error");
        logAtLevel(status, "framework error on {} {}: {}", request.getMethod(), request.getRequestURI(), message, exception);
        return build(status, code, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<WorkflowErrorResponse> handleUnexpected(Exception exception,
                                                                  HttpServletRequest request) {
        log.error("unexpected exception on {} {}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "internal server error", request);
    }

    private ResponseEntity<WorkflowErrorResponse> build(HttpStatus status,
                                                        String code,
                                                        String message,
                                                        HttpServletRequest request) {
        return build(status, code, message, request, List.of());
    }

    private ResponseEntity<WorkflowErrorResponse> build(HttpStatus status,
                                                        String code,
                                                        String message,
                                                        HttpServletRequest request,
                                                        List<WorkflowErrorResponse.ValidationViolation> violations) {
        return ResponseEntity.status(status)
                .body(WorkflowErrorResponse.of(code, message, request, violations));
    }

    private HttpStatus mapBusinessStatus(String code) {
        return switch (code) {
            case "DRAFT_NOT_FOUND", "SNAPSHOT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_WORKFLOW_STATE", "CONCURRENT_MODIFICATION", "DRAFT_OPERATION_IN_PROGRESS" -> HttpStatus.CONFLICT;
            case "UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private String normalizeMessage(String source, String fallback) {
        if (source == null || source.isBlank()) {
            return fallback;
        }
        return source;
    }

    private void logAtLevel(HttpStatus status,
                            String template,
                            Object arg1,
                            Object arg2,
                            Object arg3,
                            Throwable throwable) {
        if (status.is5xxServerError()) {
            log.error(template, arg1, arg2, arg3, throwable);
            return;
        }
        log.warn(template, arg1, arg2, arg3);
    }
}
