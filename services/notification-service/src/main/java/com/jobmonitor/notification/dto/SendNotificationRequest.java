package com.jobmonitor.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound request for sending a notification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendNotificationRequest {

    private UUID templateId;

    @NotBlank(message = "Channel is required")
    private String channel;

    @NotBlank(message = "Recipient is required")
    @Size(max = 500, message = "Recipient must not exceed 500 characters")
    private String recipient;

    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    private String body;

    @Positive(message = "Priority must be positive")
    private Integer priority;

    private Map<String, Object> templateVariables;

    private Instant scheduledAt;

    private Map<String, String> metadata;
}
