package com.jobmonitor.platform.common.exception;

/**
 * Thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(
            String.format("%s not found with id: %s", resourceType, id),
            ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    public ResourceNotFoundException(String message) {
        super(message, ErrorCode.RESOURCE_NOT_FOUND);
    }
}
