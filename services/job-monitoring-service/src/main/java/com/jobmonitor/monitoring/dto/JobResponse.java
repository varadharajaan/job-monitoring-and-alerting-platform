package com.jobmonitor.monitoring.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for job details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {
    private UUID id;
    private String tenantId;
    private String name;
    private String description;
    private String cronExpression;
    private String scheduleType;
    private Integer slaSeconds;
    private Integer gracePeriodSeconds;
    private Integer expectedRuntimeSeconds;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private List<String> tags;
    private Map<String, String> metadata;
    private String status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
