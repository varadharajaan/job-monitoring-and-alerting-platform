package com.jobmonitor.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Centralized error codes for the entire platform.
 * <p>
 * Every error response produced by any service MUST reference one of these codes.
 * This eliminates scattered string literals and provides a single source of truth
 * for client-side error handling, documentation, and observability dashboards.
 * <p>
 * Convention: {@code CATEGORY_DETAIL} — e.g. {@code VALIDATION_FAILED}, {@code RESOURCE_NOT_FOUND}.
 */
@Getter
public enum ErrorCode {

    // ═══════════════ Generic Business ═══════════════
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "General business rule violation"),

    // ═══════════════ Resource ═══════════════
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested resource does not exist"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),

    // ═══════════════ Rate Limiting ═══════════════
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Request rate limit exceeded"),

    // ═══════════════ Auth & Access ═══════════════
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Insufficient permissions"),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "Authentication credentials are invalid"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access to the requested resource is denied"),

    // ═══════════════ Availability ═══════════════
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Downstream service is unavailable"),
    REQUEST_TIMEOUT(HttpStatus.REQUEST_TIMEOUT, "Operation timed out"),

    // ═══════════════ Concurrency ═══════════════
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "Concurrent modification conflict"),

    // ═══════════════ Validation ═══════════════
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    BINDING_FAILED(HttpStatus.BAD_REQUEST, "Request binding failed"),
    CONSTRAINT_VIOLATION(HttpStatus.BAD_REQUEST, "Constraint violation"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Required request parameter is missing"),

    // ═══════════════ Request Format ═══════════════
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request body"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Request parameter type mismatch"),

    // ═══════════════ HTTP Method / Media ═══════════════
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "No endpoint matches the request"),

    // ═══════════════ Data Integrity ═══════════════
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "Data integrity constraint violated"),

    // ═══════════════ Catch-all ═══════════════
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred");

    /** The HTTP status associated with this error code. */
    private final HttpStatus httpStatus;

    /** Human-readable default message (used when no specific message is provided). */
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /**
     * Returns the wire-format code string (identical to {@link #name()}).
     * Exists for explicitness when serializing into {@link ApiError#getErrorCode()}.
     */
    public String code() {
        return name();
    }
}