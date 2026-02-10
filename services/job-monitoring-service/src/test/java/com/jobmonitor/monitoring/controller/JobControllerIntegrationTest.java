package com.jobmonitor.monitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.monitoring.dto.JobRequest;
import com.jobmonitor.monitoring.dto.JobResponse;
import com.jobmonitor.monitoring.service.JobService;
import com.jobmonitor.platform.common.dto.PageResponse;
import com.jobmonitor.platform.common.exception.DuplicateResourceException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for {@link JobController} — Spring WebMvcTest slice.
 * Uses MockBean for service layer, validates HTTP contract.
 */
@WebMvcTest(controllers = JobController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class}))
@DisplayName("JobController Integration Tests")
class JobControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JobService jobService;

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_ID = "tenant-int-001";
    private static final UUID JOB_ID = UUID.randomUUID();

    // ── Sample request/response fixtures ──

    private JobRequest sampleRequest() {
        return JobRequest.builder()
                .name("integration-test-job")
                .description("Job created during integration test")
                .cronExpression("0 0 * * *")
                .scheduleType("CRON")
                .slaSeconds(3600)
                .build();
    }

    private JobResponse sampleResponse() {
        return JobResponse.builder()
                .id(JOB_ID)
                .tenantId(TENANT_ID)
                .name("integration-test-job")
                .description("Job created during integration test")
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
    }

    // ──────────── POST /api/v1/jobs ────────────

    @Nested
    @DisplayName("POST /api/v1/jobs")
    class CreateJobEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return 201 Created with valid request")
        void shouldReturn201() throws Exception {
            given(jobService.createJob(eq(TENANT_ID), any(JobRequest.class)))
                    .willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/jobs")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest()))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.name", is("integration-test-job")))
                    .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                    .andExpect(jsonPath("$.message", containsString("registered")));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400OnBlankName() throws Exception {
            var invalidRequest = JobRequest.builder().name("").build();

            mockMvc.perform(post("/api/v1/jobs")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("should return 409 on duplicate job name")
        void shouldReturn409OnDuplicate() throws Exception {
            given(jobService.createJob(eq(TENANT_ID), any(JobRequest.class)))
                    .willThrow(new DuplicateResourceException("Job", "integration-test-job"));

            mockMvc.perform(post("/api/v1/jobs")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRequest()))
                            .with(csrf()))
                    .andExpect(status().isConflict());
        }
    }

    // ──────────── GET /api/v1/jobs/{jobId} ────────────

    @Nested
    @DisplayName("GET /api/v1/jobs/{jobId}")
    class GetJobEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return 200 with job response")
        void shouldReturn200() throws Exception {
            given(jobService.getJob(TENANT_ID, JOB_ID)).willReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/jobs/{jobId}", JOB_ID)
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.id", is(JOB_ID.toString())));
        }

        @Test
        @WithMockUser
        @DisplayName("should return 404 when job not found")
        void shouldReturn404() throws Exception {
            given(jobService.getJob(TENANT_ID, JOB_ID))
                    .willThrow(new ResourceNotFoundException("Job", JOB_ID));

            mockMvc.perform(get("/api/v1/jobs/{jobId}", JOB_ID)
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────── GET /api/v1/jobs ────────────

    @Nested
    @DisplayName("GET /api/v1/jobs")
    class ListJobsEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return paginated list")
        void shouldReturnPaginatedList() throws Exception {
            var pageResponse = PageResponse.<JobResponse>builder()
                    .content(List.of(sampleResponse()))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .first(true)
                    .last(true)
                    .build();

            given(jobService.listJobs(eq(TENANT_ID), any(Optional.class), any(Pageable.class)))
                    .willReturn(pageResponse);

            mockMvc.perform(get("/api/v1/jobs")
                            .header(TENANT_HEADER, TENANT_ID)
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.totalElements", is(1)));
        }
    }

    // ──────────── DELETE /api/v1/jobs/{jobId} ────────────

    @Nested
    @DisplayName("DELETE /api/v1/jobs/{jobId}")
    class DeactivateJobEndpoint {

        @Test
        @WithMockUser
        @DisplayName("should return 200 on deactivation")
        void shouldReturn200() throws Exception {
            mockMvc.perform(delete("/api/v1/jobs/{jobId}", JOB_ID)
                            .header(TENANT_HEADER, TENANT_ID)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("deactivated")));
        }
    }
}
