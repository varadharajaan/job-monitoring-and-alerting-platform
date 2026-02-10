package com.jobmonitor.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank private String tenantId;
    @NotBlank @Size(min = 3, max = 100) private String username;
    @NotBlank @Email private String email;
    @NotBlank @Size(min = 8, max = 128) private String password;
    private String fullName;
    private String role;
}
