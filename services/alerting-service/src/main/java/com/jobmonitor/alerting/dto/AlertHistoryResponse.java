package com.jobmonitor.alerting.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound response for an alert history entry.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHistoryResponse {
    private UUID id;
    private UUID alertRuleId;
    private String alertRuleName;
    private String tenantId;
    private UUID jobId;
    private String severity;
    private String status;
    private String message;
    private Map<String, String> contextJson;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private Instant triggeredAt;
}
