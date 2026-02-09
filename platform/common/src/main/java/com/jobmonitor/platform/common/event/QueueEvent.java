package com.jobmonitor.platform.common.event;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted by the job-queue-service when background jobs are enqueued, processed, or fail.
 */
@Getter
@Setter
@NoArgsConstructor
public class QueueEvent extends PlatformEvent {

    public enum Action { ENQUEUED, PROCESSING, COMPLETED, FAILED, RETRYING, CANCELLED, TIMED_OUT }

    private UUID queuedJobId;
    private Action action;
    private String jobType;
    private int priority;
    private int attemptNumber;
    private String workerNodeId;
    private String errorMessage;
    private Map<String, Object> payload;

    @Builder
    public QueueEvent(String tenantId, UUID queuedJobId, Action action,
                      String jobType, int priority, int attemptNumber,
                      String workerNodeId, String errorMessage, Map<String, Object> payload) {
        super(UUID.randomUUID().toString(), "QUEUE", Instant.now(), "job-queue-service", tenantId, null);
        this.queuedJobId = queuedJobId;
        this.action = action;
        this.jobType = jobType;
        this.priority = priority;
        this.attemptNumber = attemptNumber;
        this.workerNodeId = workerNodeId;
        this.errorMessage = errorMessage;
        this.payload = payload;
    }
}
