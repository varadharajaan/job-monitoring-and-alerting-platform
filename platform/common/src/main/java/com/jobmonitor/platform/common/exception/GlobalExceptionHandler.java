package com.jobmonitor.platform.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for all services.
 * <p>
 * Produces consistent {@link ApiError} JSON responses with:
 * <ul>
 *   <li>Correlation IDs (traceId from MDC)</li>
 *   <li>Structured error codes from the centralized {@link ErrorCode} enum</li>
 *   <li>Field-level validation errors</li>
 *   <li>Sanitized messages (never leaks stack traces to clients)</li>
 * </ul>
 * <p>
 * Exception priority (most specific first):
 * BusinessException hierarchy → Spring Security → Validation → JPA → Catch-all
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ═══════════════ Business Exception Hierarchy ═══════════════

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access attempt: uri={}", request.getRequestURI());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("Forbidden access: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        log.error("Service unavailable: {}", ex.getMessage(), ex.getCause());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockException ex, HttpServletRequest request) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(RequestTimeoutException.class)
    public ResponseEntity<ApiError> handleTimeout(RequestTimeoutException ex, HttpServletRequest request) {
        log.warn("Request timeout: {}", ex.getMessage());
        return buildResponse(ex, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business error: errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        return buildResponse(ex, request);
    }

    // ═══════════════ Spring Security Exceptions ═══════════════

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: uri={}, reason={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(ErrorCode.AUTHENTICATION_FAILED, "Authentication required", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: uri={}, reason={}", request.getRequestURI(), ex.getMessage());
        return buildResponse(ErrorCode.ACCESS_DENIED, "You do not have permission to access this resource", request);
    }

    // ═══════════════ Validation Exceptions ═══════════════

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ApiError.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        log.warn("Validation failed: {} field errors on {}", fieldErrors.size(), request.getRequestURI());

        var error = baseErrorBuilder(ErrorCode.VALIDATION_FAILED, "Validation failed", request)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(error);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBindException(BindException ex, HttpServletRequest request) {
        var fieldErrors = ex.getFieldErrors().stream()
                .map(fe -> ApiError.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .rejectedValue(fe.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        var error = baseErrorBuilder(ErrorCode.BINDING_FAILED, "Request binding failed", request)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(ErrorCode.BINDING_FAILED.getHttpStatus()).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        var fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> ApiError.FieldError.builder()
                        .field(cv.getPropertyPath().toString())
                        .message(cv.getMessage())
                        .rejectedValue(cv.getInvalidValue())
                        .build())
                .collect(Collectors.toList());

        var error = baseErrorBuilder(ErrorCode.CONSTRAINT_VIOLATION, "Constraint violation", request)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(ErrorCode.CONSTRAINT_VIOLATION.getHttpStatus()).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        var message = String.format("Required parameter '%s' of type '%s' is missing",
                ex.getParameterName(), ex.getParameterType());
        return buildResponse(ErrorCode.MISSING_PARAMETER, message, request);
    }

    // ═══════════════ Deserialization / Type / Method Errors ═══════════════

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildResponse(ErrorCode.MALFORMED_REQUEST, "Malformed JSON request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        var message = String.format("Parameter '%s' must be of type '%s'",
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        return buildResponse(ErrorCode.TYPE_MISMATCH, message, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED,
                String.format("HTTP method '%s' not supported for this endpoint", ex.getMethod()), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                String.format("Content type '%s' not supported", ex.getContentType()), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.ENDPOINT_NOT_FOUND,
                String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()), request);
    }

    // ═══════════════ JPA / Data Exceptions ═══════════════

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleJpaOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("JPA optimistic lock failure: {}", ex.getMessage());
        return buildResponse(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                "Resource was modified by another request. Please retry.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(ErrorCode.DATA_INTEGRITY_VIOLATION,
                "Data integrity constraint violated. Check for duplicates or references.", request);
    }

    // ═══════════════ Catch-all ═══════════════

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please contact support.", request);
    }

    // ═══════════════ Helpers ═══════════════

    /**
     * Builds a response directly from a {@link BusinessException} (uses its embedded ErrorCode).
     */
    private ResponseEntity<ApiError> buildResponse(BusinessException ex, HttpServletRequest request) {
        var error = baseErrorBuilder(ex.getErrorCode(), ex.getMessage(), request).build();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    /**
     * Builds a response from an {@link ErrorCode} and a custom message.
     */
    private ResponseEntity<ApiError> buildResponse(ErrorCode errorCode, String message,
                                                    HttpServletRequest request) {
        var error = baseErrorBuilder(errorCode, message, request).build();
        return ResponseEntity.status(errorCode.getHttpStatus()).body(error);
    }

    private ApiError.ApiErrorBuilder baseErrorBuilder(ErrorCode errorCode, String message,
                                                       HttpServletRequest request) {
        var status = errorCode.getHttpStatus();
        return ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(errorCode.code())
                .message(message)
                .path(request.getRequestURI())
                .traceId(MDC.get("traceId"));
    }
}
