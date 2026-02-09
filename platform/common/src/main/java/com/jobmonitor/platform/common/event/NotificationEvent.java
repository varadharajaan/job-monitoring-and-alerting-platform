package com.jobmonitor.platform.common.event;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted when a notification needs to be sent or delivery status changes.
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificationEvent extends PlatformEvent {

    public enum Action { REQUESTED, QUEUED, SENT, DELIVERED, FAILED, BOUNCED }
    public enum Channel { EMAIL, SMS, SLACK, PUSH, WEBHOOK }

    private UUID notificationId;
    private Action action;
    private Channel channel;
    private String templateId;
    private String recipient;
    private String subject;
    private Map<String, Object> templateVariables;
    private String providerMessageId;
    private String failureReason;

    @Builder
    public NotificationEvent(String tenantId, UUID notificationId, Action action,
                              Channel channel, String templateId, String recipient,
                              String subject, Map<String, Object> templateVariables,
                              String providerMessageId, String failureReason) {
        super(UUID.randomUUID().toString(), "NOTIFICATION", Instant.now(), "notification-service", tenantId, null);
        this.notificationId = notificationId;
        this.action = action;
        this.channel = channel;
        this.templateId = templateId;
        this.recipient = recipient;
        this.subject = subject;
        this.templateVariables = templateVariables;
        this.providerMessageId = providerMessageId;
        this.failureReason = failureReason;
    }
}