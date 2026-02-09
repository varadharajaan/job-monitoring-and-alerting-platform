package com.jobmonitor.platform.common.exception;

/**
 * Thrown when an optimistic lock conflict prevents updating a resource.
 */
public class OptimisticLockException extends BusinessException {

    public OptimisticLockException(String resourceType, Object id) {
        super(
            String.format("Concurrent update conflict on %s with id: %s. Please retry.", resourceType, id),
            ErrorCode.OPTIMISTIC_LOCK_CONFLICT
        );
    }
}
