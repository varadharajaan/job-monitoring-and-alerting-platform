package com.jobmonitor.platform.common.functional;

import java.util.Map;

/**
 * Functional interface for evaluating alert conditions.
 * Each rule type (FAILURE_THRESHOLD, SLA_VIOLATION, etc.) provides
 * its own implementation via the alert condition registry.
 */
@FunctionalInterface
public interface AlertConditionEvaluator {

    /**
     * Evaluate whether the alert condition is met.
     *
     * @param conditionJson the condition configuration as a map
     * @param context       execution context (metric values, counts, etc.)
     * @return true if the alert should fire
     */
    boolean evaluate(Map<String, Object> conditionJson, Map<String, Object> context);
}
