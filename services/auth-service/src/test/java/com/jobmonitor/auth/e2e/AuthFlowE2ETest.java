package com.jobmonitor.auth.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.platform.common.test.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the Auth Service.
 *
 * <p>Exercises the FULL auth lifecycle: Register → Login → Get Profile → Refresh Token
 * → Create API Key → List API Keys → Revoke API Key</p>
 *
 * <p>Uses real TimescaleDB (Testcontainers) with Flyway migrations creating the
 * users and api_keys tables.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Auth Flow E2E Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowE2ETest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static String tenantId;
    private static String accessToken;
    private static String refreshToken;
    private static String userId;
    private static String apiKeyId;

    @BeforeAll
    static void seedTenant(@Autowired JdbcTemplate jdbc) {
        tenantId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO tenants (id, name, slug, plan, status) VALUES (?::uuid, ?, ?, 'FREE', 'ACTIVE')",
                tenantId, "auth-e2e-" + tenantId.substring(0, 8), "auth-e2e-" + tenantId.substring(0, 8)
        );
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Register user via POST /api/v1/auth/register")
    void registerUser() throws Exception {
        var registerRequest = Map.of(
                "tenantId", tenantId,
                "username", "e2e-test-user",
                "email", "e2e-test@example.com",
                "password", "SecureP@ssw0rd!",
                "fullName", "E2E Test User",
                "role", "ADMIN"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("e2e-test-user")))
                .andExpect(jsonPath("$.data.email", is("e2e-test@example.com")))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andExpect(jsonPath("$.data.enabled", is(true)))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        userId = body.path("data").path("id").asText();
        assertThat(userId).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Login via POST /api/v1/auth/login")
    void loginUser() throws Exception {
        var loginRequest = Map.of(
                "username", "e2e-test-user",
                "password", "SecureP@ssw0rd!"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.username", is("e2e-test-user")))
                .andExpect(jsonPath("$.data.tenantId", is(tenantId)))
                .andExpect(jsonPath("$.data.role", is("ADMIN")))
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = body.path("data").path("accessToken").asText();
        refreshToken = body.path("data").path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Login with wrong password returns 401")
    void loginWrongPasswordFails() throws Exception {
        var loginRequest = Map.of(
                "username", "e2e-test-user",
                "password", "WrongPassword"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Get current user via GET /api/v1/auth/me")
    void getCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username", is("e2e-test-user")))
                .andExpect(jsonPath("$.data.email", is("e2e-test@example.com")));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Refresh token via POST /api/v1/auth/refresh")
    void refreshTokenFlow() throws Exception {
        var refreshRequest = Map.of("refreshToken", refreshToken);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.username", is("e2e-test-user")))
                .andReturn();

        // Update tokens for subsequent tests
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = body.path("data").path("accessToken").asText();
        refreshToken = body.path("data").path("refreshToken").asText();
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Create API key via POST /api/v1/auth/api-keys")
    void createApiKey() throws Exception {
        var apiKeyRequest = Map.of(
                "name", "e2e-ci-key",
                "scopes", List.of("READ", "WRITE")
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/api-keys")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(apiKeyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("e2e-ci-key")))
                .andExpect(jsonPath("$.data.rawKey", startsWith("jm_")))
                .andExpect(jsonPath("$.data.scopes", hasSize(2)))
                .andExpect(jsonPath("$.data.enabled", is(true)))
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        apiKeyId = body.path("data").path("id").asText();
        assertThat(apiKeyId).isNotBlank();
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: List API keys via GET /api/v1/auth/api-keys")
    void listApiKeys() throws Exception {
        mockMvc.perform(get("/api/v1/auth/api-keys")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].name", is("e2e-ci-key")));
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Revoke API key via DELETE /api/v1/auth/api-keys/{keyId}")
    void revokeApiKey() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/api-keys/{keyId}", apiKeyId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Verify key no longer in active list
        mockMvc.perform(get("/api/v1/auth/api-keys")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + apiKeyId + "')]").doesNotExist());
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Duplicate username returns 409")
    void duplicateUsernameReturns409() throws Exception {
        var duplicateRequest = Map.of(
                "tenantId", tenantId,
                "username", "e2e-test-user",
                "email", "different@example.com",
                "password", "Password123!"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(10)
    @DisplayName("Step 10: Unauthenticated GET /me returns 401/403")
    void unauthenticatedFails() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is4xxClientError());
    }
}
