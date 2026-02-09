package com.jobmonitor.platform.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base business exception for the platform.
 * All domain-specific exceptions should extend this.
 * <p>
 * Error codes are sourced exclusively from the centralized {@link ErrorCode} enum —
 * no scattered string literals.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(message, ErrorCode.BUSINESS_ERROR);
    }

    public BusinessException(String message, ErrorCode errorCode) {
        this(message, errorCode.getHttpStatus(), errorCode);
    }

    public BusinessException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public BusinessException(String message, Throwable cause, HttpStatus status, ErrorCode errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Returns the wire-format error code string for API serialization.
     */
    public String getErrorCodeValue() {
        return errorCode.code();
    }
}
