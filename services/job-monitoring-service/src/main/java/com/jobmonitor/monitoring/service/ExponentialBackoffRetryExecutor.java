package com.jobmonitor.monitoring.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.functional.RetryExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Retry engine with exponential backoff.
 * Uses the {@link RetryExecutor} functional interface defined in platform/common.
 * <p>
 * Backoff formula: delay = initialDelayMs * (multiplier ^ attemptIndex)
 * <p>
 * Each attempt is logged. On final failure, the exception propagates.
 *
 * @param <T> the result type
 */
@Slf4j
@Component
public class ExponentialBackoffRetryExecutor<T> implements RetryExecutor<T> {

    private static final long BASE_DELAY_MS = 1000L;

    private final double backoffMultiplier;

    public ExponentialBackoffRetryExecutor(PlatformProperties properties) {
        this.backoffMultiplier = properties.getJobMonitor().getRetryBackoffMultiplier();
    }

    @Override
    public T executeWithRetry(Supplier<T> action, int maxRetries, String operationName) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {}/{} for '{}'", attempt, maxRetries, operationName);
                }
                return action.get();
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt > maxRetries) {
                    log.error("All {} retries exhausted for '{}': {}",
                            maxRetries, operationName, e.getMessage());
                    break;
                }

                long delay = calculateDelay(attempt);
                log.warn("Attempt {} failed for '{}', retrying in {}ms: {}",
                        attempt, operationName, delay, e.getMessage());

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted for: " + operationName, ie);
                }
            }
        }

        throw new RuntimeException(
                String.format("Operation '%s' failed after %d retries", operationName, maxRetries),
                lastException);
    }

    private long calculateDelay(int attempt) {
        return (long) (BASE_DELAY_MS * Math.pow(backoffMultiplier, attempt - 1));
    }
}
