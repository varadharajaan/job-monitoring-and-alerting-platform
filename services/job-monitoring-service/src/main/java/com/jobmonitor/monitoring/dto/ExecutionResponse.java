package com.jobmonitor.monitoring.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for job execution details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionResponse {
    private UUID id;
    private UUID jobId;
    private String tenantId;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private Integer exitCode;
    private String output;
    private String errorMessage;
    private Integer attemptNumber;
    private Map<String, String> metadata;
    private Instant createdAt;
}
