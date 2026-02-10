package com.jobmonitor.platform.common.functional;

/**
 * Functional interface for domain-level validation of entities before persistence.
 * Implementations throw {@link com.jobmonitor.platform.common.exception.BusinessException}
 * on validation failure.
 *
 * @param <T> the entity type to validate
 */
@FunctionalInterface
public interface EntityValidator<T> {

    /**
     * Validate the entity. Throw a BusinessException if invalid.
     *
     * @param entity the entity to validate
     */
    void validate(T entity);
}
