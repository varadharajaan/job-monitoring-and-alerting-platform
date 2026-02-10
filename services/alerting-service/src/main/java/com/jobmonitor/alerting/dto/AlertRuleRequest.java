package com.jobmonitor.alerting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Inbound request for creating/updating an alert rule.
 * All constraints are annotation-driven — no hardcoded validation logic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleRequest {

    @NotBlank(message = "Alert rule name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private UUID jobId;

    @NotBlank(message = "Rule type is required")
    @Size(max = 50, message = "Rule type must not exceed 50 characters")
    private String ruleType;

    @NotNull(message = "Condition JSON is required")
    private String conditionJson;

    private String severity;

    private List<String> notificationChannels;

    @Positive(message = "Cooldown seconds must be positive")
    private Integer cooldownSeconds;
}
