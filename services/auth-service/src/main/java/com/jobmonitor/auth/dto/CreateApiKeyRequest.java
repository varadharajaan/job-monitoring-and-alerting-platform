package com.jobmonitor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class CreateApiKeyRequest {
    @NotBlank private String name;
    private List<String> scopes;
    private Instant expiresAt;
}
