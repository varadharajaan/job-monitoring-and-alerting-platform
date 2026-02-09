package com.jobmonitor.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a service dependency (Kafka, Redis, S3, etc.) is unavailable.
 */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String serviceName) {
        super(
            String.format("Downstream service unavailable: %s", serviceName),
            ErrorCode.SERVICE_UNAVAILABLE
        );
    }

    public ServiceUnavailableException(String serviceName, Throwable cause) {
        super(
            String.format("Downstream service unavailable: %s", serviceName),
            cause,
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.SERVICE_UNAVAILABLE
        );
    }
}
