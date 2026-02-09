package com.jobmonitor.platform.common.event;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted by the job-monitoring-service when a job starts, completes, fails,
 * or violates its SLA.
 */
@Getter
@Setter
@NoArgsConstructor
public class JobEvent extends PlatformEvent {

    public enum Action { REGISTERED, STARTED, COMPLETED, FAILED, SLA_VIOLATED, RETRYING, HEARTBEAT_MISSED }

    private UUID jobId;
    private UUID executionId;
    private Action action;
    private String jobName;
    private String cronExpression;
    private Integer exitCode;
    private String errorMessage;
    private Instant executionStartTime;
    private Instant executionEndTime;
    private Map<String, String> metadata;

    @Builder
    public JobEvent(String tenantId, UUID jobId, UUID executionId, Action action,
                    String jobName, String cronExpression, Integer exitCode,
                    String errorMessage, Instant executionStartTime, Instant executionEndTime,
                    Map<String, String> metadata) {
        super(UUID.randomUUID().toString(), "JOB", Instant.now(), "job-monitoring-service", tenantId, null);
        this.jobId = jobId;
        this.executionId = executionId;
        this.action = action;
        this.jobName = jobName;
        this.cronExpression = cronExpression;
        this.exitCode = exitCode;
        this.errorMessage = errorMessage;
        this.executionStartTime = executionStartTime;
        this.executionEndTime = executionEndTime;
        this.metadata = metadata;
    }
}
