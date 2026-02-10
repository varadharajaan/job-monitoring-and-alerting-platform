package com.jobmonitor.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.auth.dto.*;
import com.jobmonitor.auth.service.AuthService;
import com.jobmonitor.platform.common.exception.BusinessException;
import com.jobmonitor.platform.common.exception.ErrorCode;
import com.jobmonitor.platform.common.security.JwtAuthenticationFilter;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
import com.jobmonitor.platform.common.security.SecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class}))
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private AuthService authService;

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_ID = "tenant-auth-int";
    private static final UUID USER_ID = UUID.randomUUID();

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .accessToken("access-token-123").refreshToken("refresh-token-456")
                .tokenType("Bearer").expiresIn(3600)
                .userId(USER_ID.toString()).username("testuser")
                .tenantId(TENANT_ID).role("OPERATOR")
                .issuedAt(Instant.now()).build();
    }

    private UserResponse sampleUserResponse() {
        return UserResponse.builder()
                .id(USER_ID).tenantId(TENANT_ID).username("testuser")
                .email("test@example.com").fullName("Test User")
                .role("OPERATOR").enabled(true).createdAt(Instant.now()).build();
    }

    @Nested @DisplayName("POST /api/v1/auth/login")
    class LoginEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 200 with auth response on valid login")
        void shouldReturn200() throws Exception {
            given(authService.login(any(LoginRequest.class))).willReturn(sampleAuthResponse());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"testuser\",\"password\":\"password123\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken", is("access-token-123")))
                    .andExpect(jsonPath("$.data.tokenType", is("Bearer")));
        }

        @Test @WithMockUser
        @DisplayName("should return 400 when username is blank")
        void shouldReturn400OnBlankUsername() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\",\"password\":\"password123\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser
        @DisplayName("should return error on invalid credentials")
        void shouldReturnErrorOnInvalidCredentials() throws Exception {
            given(authService.login(any(LoginRequest.class)))
                    .willThrow(new BusinessException("Invalid username or password",
                            ErrorCode.AUTHENTICATION_FAILED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"testuser\",\"password\":\"wrong\"}")
                            .with(csrf()))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested @DisplayName("POST /api/v1/auth/register")
    class RegisterEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 201 on successful registration")
        void shouldReturn201() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(sampleUserResponse());

            var request = "{\"tenantId\":\"" + TENANT_ID + "\",\"username\":\"newuser\"," +
                    "\"email\":\"new@example.com\",\"password\":\"password123\",\"fullName\":\"New User\"}";

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request)
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username", is("testuser")));
        }

        @Test @WithMockUser
        @DisplayName("should return 400 when required fields missing")
        void shouldReturn400OnMissingFields() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /api/v1/auth/refresh")
    class RefreshEndpoint {

        @Test @WithMockUser
        @DisplayName("should return new tokens on valid refresh")
        void shouldRefreshTokens() throws Exception {
            given(authService.refreshToken(anyString())).willReturn(sampleAuthResponse());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"valid-token\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").exists());
        }
    }

    @Nested @DisplayName("GET /api/v1/auth/me")
    class GetCurrentUserEndpoint {

        @Test @WithMockUser(username = "user-id-123")
        @DisplayName("should return current user profile")
        void shouldReturnCurrentUser() throws Exception {
            given(authService.getCurrentUser("user-id-123")).willReturn(sampleUserResponse());

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username", is("testuser")));
        }
    }

    @Nested @DisplayName("POST /api/v1/auth/api-keys")
    class CreateApiKeyEndpoint {

        @Test @WithMockUser(username = "user-id-123")
        @DisplayName("should return 201 with api key response")
        void shouldReturn201() throws Exception {
            var keyResponse = ApiKeyResponse.builder()
                    .id(UUID.randomUUID()).name("my-key").rawKey("jm_abc123")
                    .keyPrefix("jm_abc1").scopes(List.of("READ"))
                    .enabled(true).createdAt(Instant.now()).build();
            given(authService.createApiKey(eq("user-id-123"), eq(TENANT_ID), any(CreateApiKeyRequest.class)))
                    .willReturn(keyResponse);

            mockMvc.perform(post("/api/v1/auth/api-keys")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"my-key\"}")
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name", is("my-key")))
                    .andExpect(jsonPath("$.data.rawKey", startsWith("jm_")));
        }
    }

    @Nested @DisplayName("GET /api/v1/auth/api-keys")
    class ListApiKeysEndpoint {

        @Test @WithMockUser(username = "user-id-123")
        @DisplayName("should return list of api keys")
        void shouldReturnApiKeys() throws Exception {
            given(authService.listApiKeys("user-id-123")).willReturn(List.of(
                    ApiKeyResponse.builder().id(UUID.randomUUID()).name("key-1")
                            .keyPrefix("jm_abc1").scopes(List.of("READ"))
                            .enabled(true).createdAt(Instant.now()).build()));

            mockMvc.perform(get("/api/v1/auth/api-keys"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }
    }

    @Nested @DisplayName("DELETE /api/v1/auth/api-keys/{keyId}")
    class RevokeApiKeyEndpoint {

        @Test @WithMockUser(username = "user-id-123")
        @DisplayName("should return 204 on successful revocation")
        void shouldReturn204() throws Exception {
            var keyId = UUID.randomUUID();
            mockMvc.perform(delete("/api/v1/auth/api-keys/{keyId}", keyId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }
}
