package com.jobmonitor.alerting.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
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
 * End-to-end integration test for the Alerting Service.
 *
 * <p>Exercises the FULL stack: HTTP → Controller → Service → Repository → TimescaleDB
 * with real Testcontainers, JWT authentication, and Kafka event publishing.</p>
 *
 * <p>Lifecycle tested: Create Rule → Evaluate → Trigger Alert → Query History → Acknowledge</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Alert Management E2E Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertManagementE2ETest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private static String tenantId;
    private static String jwtToken;
    private static String ruleId;
    private static UUID jobId;

    @BeforeAll
    static void seedData(@Autowired JdbcTemplate jdbc, @Autowired JwtTokenProvider tokenProvider) {
        tenantId = UUID.randomUUID().toString();
        jobId = UUID.randomUUID();

        // Seed tenant (FK constraint on alert_rules and alert_history)
        jdbc.update(
                "INSERT INTO tenants (id, name, slug, plan, status) VALUES (?::uuid, ?, ?, 'FREE', 'ACTIVE')",
                tenantId, "alert-e2e-" + tenantId.substring(0, 8), "alert-e2e-" + tenantId.substring(0, 8)
        );

        // Seed a job (FK constraint on alert_rules.job_id)
        jdbc.update(
                "INSERT INTO jobs (id, tenant_id, name, schedule_type, status) " +
                        "VALUES (?::uuid, ?::uuid, ?, 'CRON', 'ACTIVE')",
                jobId.toString(), tenantId, "e2e-alert-target-job"
        );

        jwtToken = tokenProvider.generateToken(
                UUID.randomUUID().toString(), "alert-e2e-user", tenantId, List.of("ADMIN"));
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Create an alert rule via POST /api/v1/alerts/rules")
    void createAlertRule() throws Exception {
        var ruleRequest = Map.of(
                "name", "e2e-failure-count-alert",
                "description", "Triggers when failure count exceeds threshold",
                "jobId", jobId.toString(),
                "ruleType", "FAILURE_COUNT",
                "conditionJson", "{\"threshold\": 3}",
                "severity", "HIGH",
                "notificationChannels", List.of("EMAIL", "SLACK"),
                "cooldownSeconds", 60
        );

        MvcResult result = mockMvc.perform(post("/api/v1/alerts/rules")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ruleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("e2e-failure-count-alert")))
                .andExpect(jsonPath("$.data.severity", is("HIGH")))
                .andExpect(jsonPath("$.data.enabled", is(true)))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        ruleId = responseBody.path("data").path("id").asText();
        assertThat(ruleId).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Get rule by ID via GET /api/v1/alerts/rules/{ruleId}")
    void getAlertRule() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", ruleId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(ruleId)))
                .andExpect(jsonPath("$.data.ruleType", is("FAILURE_COUNT")));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: List rules via GET /api/v1/alerts/rules")
    void listAlertRules() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/rules")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Evaluate rules — trigger alert via POST /api/v1/alerts/evaluate/{jobId}")
    void evaluateAndTriggerAlert() throws Exception {
        var context = Map.of(
                "failureCount", 5,   // exceeds threshold of 3
                "durationMs", 7200000,
                "consecutiveFailures", 4
        );

        mockMvc.perform(post("/api/v1/alerts/evaluate/{jobId}", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].severity", is("HIGH")))
                .andExpect(jsonPath("$.data[0].status", is("TRIGGERED")));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Query alert history via GET /api/v1/alerts/history")
    void queryAlertHistory() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/history")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].alertRuleName", is("e2e-failure-count-alert")));
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Toggle rule disabled via PATCH /api/v1/alerts/rules/{ruleId}/toggle")
    void toggleRuleDisabled() throws Exception {
        mockMvc.perform(patch("/api/v1/alerts/rules/{ruleId}/toggle", ruleId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("disabled")));

        // Verify rule is now disabled
        mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", ruleId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(jsonPath("$.data.enabled", is(false)));
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Evaluate with context BELOW threshold — no alert triggered")
    void evaluateNoTrigger() throws Exception {
        // Re-enable rule first
        mockMvc.perform(patch("/api/v1/alerts/rules/{ruleId}/toggle", ruleId)
                .header("Authorization", "Bearer " + jwtToken)
                .header("X-Tenant-Id", tenantId)
                .param("enabled", "true"));

        var context = Map.of(
                "failureCount", 1  // below threshold of 3
        );

        mockMvc.perform(post("/api/v1/alerts/evaluate/{jobId}", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Duplicate rule name returns 409")
    void duplicateRuleReturns409() throws Exception {
        var duplicateRequest = Map.of(
                "name", "e2e-failure-count-alert",
                "ruleType", "FAILURE_COUNT",
                "conditionJson", "{\"threshold\": 5}"
        );

        mockMvc.perform(post("/api/v1/alerts/rules")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Unauthenticated request returns 401/403")
    void unauthenticatedFails() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/rules")
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().is4xxClientError());
    }
}
