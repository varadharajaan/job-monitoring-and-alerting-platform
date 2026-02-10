package com.jobmonitor.platform.common.functional;

import com.jobmonitor.platform.common.event.PlatformEvent;

/**
 * Functional interface for publishing platform events to a topic.
 * Decouples services from Kafka producer details for testability.
 */
@FunctionalInterface
public interface EventPublisher<E extends PlatformEvent> {

    /**
     * Publish an event to the target topic.
     *
     * @param topic the Kafka topic name
     * @param key   the message key (typically tenantId or entityId)
     * @param event the event payload
     */
    void publish(String topic, String key, E event);
}
