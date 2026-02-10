package com.jobmonitor.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Inbound request for creating/updating a notification template.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Channel is required")
    @Size(max = 20, message = "Channel must not exceed 20 characters")
    private String channel;

    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @NotNull(message = "Body is required")
    private String body;

    private List<String> variables;
}
