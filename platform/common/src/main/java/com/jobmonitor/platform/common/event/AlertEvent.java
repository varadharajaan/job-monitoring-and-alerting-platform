package com.jobmonitor.platform.common.event;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when an alert is triggered, acknowledged, or resolved.
 */
@Getter
@Setter
@NoArgsConstructor
public class AlertEvent extends PlatformEvent {

    public enum Action { TRIGGERED, ACKNOWLEDGED, RESOLVED, ESCALATED }
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    private UUID alertId;
    private UUID ruleId;
    private Action action;
    private Severity severity;
    private String alertName;
    private String description;
    private String[] channels;
    private Map<String, String> context;

    @Builder
    public AlertEvent(String tenantId, UUID alertId, UUID ruleId, Action action,
                      Severity severity, String alertName, String description,
                      String[] channels, Map<String, String> context) {
        super(UUID.randomUUID().toString(), "ALERT", Instant.now(), "alerting-service", tenantId, null);
        this.alertId = alertId;
        this.ruleId = ruleId;
        this.action = action;
        this.severity = severity;
        this.alertName = alertName;
        this.description = description;
        this.channels = channels;
        this.context = context;
    }
}
