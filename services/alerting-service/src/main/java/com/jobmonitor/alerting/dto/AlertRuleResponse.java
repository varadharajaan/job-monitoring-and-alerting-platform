package com.jobmonitor.alerting.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound response for an alert rule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRuleResponse {

    private UUID id;
    private String tenantId;
    private String name;
    private String description;
    private UUID jobId;
    private String ruleType;
    private String conditionJson;
    private String severity;
    private List<String> notificationChannels;
    private Integer cooldownSeconds;
    private Boolean enabled;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
