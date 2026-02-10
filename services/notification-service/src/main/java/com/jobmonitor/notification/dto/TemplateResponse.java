package com.jobmonitor.notification.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbound response for a notification template.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateResponse {

    private UUID id;
    private String tenantId;
    private String name;
    private String channel;
    private String subject;
    private String body;
    private List<String> variables;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
