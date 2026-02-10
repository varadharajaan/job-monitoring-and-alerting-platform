package com.jobmonitor.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyResponse {
    private UUID id;
    private String name;
    /** Only returned on creation — never stored or exposed again */
    private String rawKey;
    private String keyPrefix;
    private List<String> scopes;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private boolean enabled;
    private Instant createdAt;
}
