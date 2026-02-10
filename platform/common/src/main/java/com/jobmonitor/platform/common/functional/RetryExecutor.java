package com.jobmonitor.platform.common.functional;

import java.util.function.Supplier;

/**
 * Functional interface for retry-able execution with configurable backoff.
 *
 * @param <T> the result type
 */
@FunctionalInterface
public interface RetryExecutor<T> {

    /**
     * Execute the given supplier with retry semantics.
     *
     * @param action       the operation to execute
     * @param maxRetries   maximum retry attempts
     * @param operationName label for logging
     * @return the result of the operation
     */
    T executeWithRetry(Supplier<T> action, int maxRetries, String operationName);
}
