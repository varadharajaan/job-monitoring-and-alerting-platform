package com.jobmonitor.jobqueue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
public class EnqueueRequest {
    @NotBlank(message = "Job type is required")
    private String jobType;
    private Map<String, Object> payload;
    @Min(1) @Max(10)
    private int priority = 5;
    private int maxAttempts = 3;
    private Instant scheduledAt;
}
