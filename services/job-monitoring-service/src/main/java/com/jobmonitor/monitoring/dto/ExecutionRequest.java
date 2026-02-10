package com.jobmonitor.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.Map;

/**
 * Request DTO for recording a job execution.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionRequest {

    @NotBlank(message = "Execution status is required")
    private String status;

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private Integer exitCode;
    private String output;
    private String errorMessage;
    private Integer attemptNumber;
    private Map<String, String> metadata;
}
