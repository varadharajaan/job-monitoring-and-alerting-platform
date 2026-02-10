package com.jobmonitor.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating or updating a monitored job.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRequest {

    @NotBlank(message = "Job name is required")
    @Size(max = 255, message = "Job name must not exceed 255 characters")
    private String name;

    private String description;

    @Size(max = 100, message = "Cron expression must not exceed 100 characters")
    private String cronExpression;

    private String scheduleType;

    @Positive(message = "SLA seconds must be positive")
    private Integer slaSeconds;

    @Positive(message = "Grace period must be positive")
    private Integer gracePeriodSeconds;

    @Positive(message = "Expected runtime must be positive")
    private Integer expectedRuntimeSeconds;

    @Positive(message = "Timeout must be positive")
    private Integer timeoutSeconds;

    @Positive(message = "Max retries must be positive")
    private Integer maxRetries;

    private List<String> tags;

    private Map<String, String> metadata;
}
