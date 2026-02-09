package com.jobmonitor.platform.common.exception;

/**
 * Thrown when a duplicate resource creation is attempted.
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resourceType, String identifier) {
        super(
            String.format("%s already exists with identifier: %s", resourceType, identifier),
            ErrorCode.DUPLICATE_RESOURCE
        );
    }
}
