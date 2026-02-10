package com.jobmonitor.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private String tenantId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private boolean enabled;
    private Instant lastLoginAt;
    private Instant createdAt;
}
