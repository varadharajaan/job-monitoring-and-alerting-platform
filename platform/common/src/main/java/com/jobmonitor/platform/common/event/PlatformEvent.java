package com.jobmonitor.platform.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Base event for all Kafka messages in the platform.
 * Uses Jackson polymorphic deserialization so consumers can handle typed events.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = JobEvent.class,          name = "JOB"),
    @JsonSubTypes.Type(value = AlertEvent.class,        name = "ALERT"),
    @JsonSubTypes.Type(value = NotificationEvent.class, name = "NOTIFICATION"),
    @JsonSubTypes.Type(value = QueueEvent.class,        name = "QUEUE")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class PlatformEvent {

    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private Instant timestamp = Instant.now();
    private String source;
    private String tenantId;
    private String correlationId;
}
