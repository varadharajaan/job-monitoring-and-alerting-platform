package com.jobmonitor.notification.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound response for a notification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private UUID id;
    private String tenantId;
    private UUID templateId;
    private String channel;
    private String recipient;
    private String subject;
    private String body;
    private String status;
    private Integer priority;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private Map<String, String> metadata;
    private Instant scheduledAt;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant createdAt;
}
