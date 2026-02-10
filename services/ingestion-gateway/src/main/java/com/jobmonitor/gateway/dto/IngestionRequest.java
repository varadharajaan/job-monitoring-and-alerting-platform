package com.jobmonitor.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * Ingestion payload accepted from external job agents.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestionRequest {
    @NotBlank(message = "Job name is required")
    private String jobName;

    @NotBlank(message = "Event type is required")
    private String eventType;  // STARTED, COMPLETED, FAILED, HEARTBEAT

    private Integer exitCode;
    private String errorMessage;
    private String cronExpression;
    private Map<String, String> metadata;
}
