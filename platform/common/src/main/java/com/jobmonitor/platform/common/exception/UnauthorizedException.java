package com.jobmonitor.platform.common.exception;

/**
 * Thrown when a request is unauthorized (missing or invalid credentials).
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message, ErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException() {
        super("Authentication required", ErrorCode.UNAUTHORIZED);
    }
}
