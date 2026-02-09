package com.jobmonitor.platform.common.exception;

/**
 * Thrown when a request times out (e.g., external API call, queue lock acquisition).
 */
public class RequestTimeoutException extends BusinessException {

    public RequestTimeoutException(String operation) {
        super(
            String.format("Operation timed out: %s", operation),
            ErrorCode.REQUEST_TIMEOUT
        );
    }

    public RequestTimeoutException(String operation, long timeoutMs) {
        super(
            String.format("Operation '%s' timed out after %d ms", operation, timeoutMs),
            ErrorCode.REQUEST_TIMEOUT
        );
    }
}