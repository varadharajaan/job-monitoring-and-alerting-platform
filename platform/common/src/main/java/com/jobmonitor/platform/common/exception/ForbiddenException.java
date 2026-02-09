package com.jobmonitor.platform.common.exception;

/**
 * Thrown when the principal lacks permission for the requested operation.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String resource) {
        super(
            String.format("Access denied to resource: %s", resource),
            ErrorCode.FORBIDDEN
        );
    }

    public ForbiddenException(String resource, String action) {
        super(
            String.format("Access denied: cannot %s on %s", action, resource),
            ErrorCode.FORBIDDEN
        );
    }
}
