package com.jobmonitor.platform.common.exception;

/**
 * Thrown when a rate limit is exceeded (per-channel notification limits, API throttling, etc.)
 */
public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String channel) {
        super(
            String.format("Rate limit exceeded for channel: %s", channel),
            ErrorCode.RATE_LIMIT_EXCEEDED
        );
    }

    public RateLimitExceededException(String channel, int limit, String window) {
        super(
            String.format("Rate limit exceeded for channel '%s': max %d per %s", channel, limit, window),
            ErrorCode.RATE_LIMIT_EXCEEDED
        );
    }
}
