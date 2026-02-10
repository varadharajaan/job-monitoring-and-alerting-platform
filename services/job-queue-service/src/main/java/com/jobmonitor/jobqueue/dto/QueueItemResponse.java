package com.jobmonitor.jobqueue.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueItemResponse {
    private UUID id;
    private String tenantId;
    private String jobType;
    private Map<String, Object> payload;
    private int priority;
    private String status;
    private String assignedWorker;
    private int attemptCount;
    private int maxAttempts;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private Instant createdAt;
}
