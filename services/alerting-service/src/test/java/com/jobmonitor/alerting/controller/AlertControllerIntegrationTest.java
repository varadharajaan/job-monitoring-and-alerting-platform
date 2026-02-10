package com.jobmonitor.alerting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.alerting.dto.AlertHistoryResponse;
import com.jobmonitor.alerting.dto.AlertRuleRequest;
import com.jobmonitor.alerting.dto.AlertRuleResponse;
import com.jobmonitor.alerting.service.AlertService;
import com.jobmonitor.platform.common.dto.ApiResponse;
import com.jobmonitor.platform.common.dto.PageResponse;
import com.jobmonitor.platform.common.exception.ResourceNotFoundException;
import com.jobmonitor.platform.common.security.JwtAuthenticationFilter;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
import com.jobmonitor.platform.common.security.SecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for {@link AlertController} — WebMvcTest slice.
 */
@WebMvcTest(controllers = AlertController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class}))
@DisplayName("AlertController Integration Tests")
class AlertControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AlertService alertService;

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_ID = "tenant-int-002";
    private static final UUID RULE_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    private AlertRuleRequest sampleRequest() {
        return AlertRuleRequest.builder()
                .name("sla-breach-rule")
                .ruleType("SLA_BREACH")
                .conditionJson("{\"threshold\": 3600}")
                .severity("HIGH")
                .notificationChannels(List.of("EMAIL", "SLACK"))
                .cooldownSeconds(600)
                .build();
    }

    private AlertRuleResponse sampleResponse() {
        return AlertRuleResponse.builder()
                .id(RULE_ID)
                .tenantId(TENANT_ID)
                .name("sla-breach-rule")
                .ruleType("SLA_BREACH")
                .severity("HIGH")
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    // ──────────── POST /api/v1/alerts/rules ────────────

    @Nested
    @DisplayName("POST /api/v1/alerts/rules")
    class CreateRuleEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return 201 with valid alert rule request")
        void shouldReturn201() throws Exception {
            given(alertService.createRule(eq(TENANT_ID), any(AlertRuleRequest.class)))
                    .willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/alerts/rules")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest()))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.ruleType", is("SLA_BREACH")))
                    .andExpect(jsonPath("$.data.severity", is("HIGH")));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when rule name is blank")
        void shouldReturn400() throws Exception {
            var invalid = AlertRuleRequest.builder()
                    .name("")
                    .ruleType("FAILURE_COUNT")
                    .conditionJson("{}")
                    .build();

            mockMvc.perform(post("/api/v1/alerts/rules")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────── GET /api/v1/alerts/rules/{ruleId} ────────────

    @Nested
    @DisplayName("GET /api/v1/alerts/rules/{ruleId}")
    class GetRuleEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return 200 with rule response")
        void shouldReturn200() throws Exception {
            given(alertService.getRule(TENANT_ID, RULE_ID)).willReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", RULE_ID)
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id", is(RULE_ID.toString())));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when rule not found")
        void shouldReturn404() throws Exception {
            given(alertService.getRule(TENANT_ID, RULE_ID))
                    .willThrow(new ResourceNotFoundException("AlertRule", RULE_ID));

            mockMvc.perform(get("/api/v1/alerts/rules/{ruleId}", RULE_ID)
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────── POST /api/v1/alerts/evaluate/{jobId} ────────────

    @Nested
    @DisplayName("POST /api/v1/alerts/evaluate/{jobId}")
    class EvaluateEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return triggered alerts")
        void shouldReturnTriggered() throws Exception {
            var triggered = List.of(
                    AlertHistoryResponse.builder()
                            .id(UUID.randomUUID())
                            .alertRuleId(RULE_ID)
                            .severity("HIGH")
                            .status("TRIGGERED")
                            .triggeredAt(Instant.now())
                            .build());

            given(alertService.evaluateRules(eq(TENANT_ID), eq(JOB_ID), any()))
                    .willReturn(triggered);

            mockMvc.perform(post("/api/v1/alerts/evaluate/{jobId}", JOB_ID)
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"failureCount\": 10}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].severity", is("HIGH")));
        }
    }

    // ──────────── PATCH /api/v1/alerts/rules/{ruleId}/toggle ────────────

    @Nested
    @DisplayName("PATCH /api/v1/alerts/rules/{ruleId}/toggle")
    class ToggleEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should toggle and return 200")
        void shouldToggle() throws Exception {
            mockMvc.perform(patch("/api/v1/alerts/rules/{ruleId}/toggle", RULE_ID)
                            .header(TENANT_HEADER, TENANT_ID)
                            .param("enabled", "false")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("disabled")));
        }
    }
}
