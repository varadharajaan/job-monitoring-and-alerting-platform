package com.jobmonitor.jobqueue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.jobqueue.dto.EnqueueRequest;
import com.jobmonitor.jobqueue.dto.QueueItemResponse;
import com.jobmonitor.jobqueue.dto.QueueStatsResponse;
import com.jobmonitor.jobqueue.service.JobQueueService;
import com.jobmonitor.jobqueue.service.QueueStatsService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

@WebMvcTest(controllers = JobQueueController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class}))
@DisplayName("JobQueueController Integration Tests")
class JobQueueControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private JobQueueService queueService;
    @MockBean private QueueStatsService statsService;

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String WORKER_HEADER = "X-Worker-Id";
    private static final String TENANT_ID = "tenant-queue-int";
    private static final UUID ITEM_ID = UUID.randomUUID();

    private QueueItemResponse sampleResponse() {
        return QueueItemResponse.builder()
                .id(ITEM_ID).tenantId(TENANT_ID).jobType("DATA_PIPELINE")
                .payload(Map.of("source", "s3://bucket/data")).priority(3)
                .status("PENDING").attemptCount(0).maxAttempts(3)
                .createdAt(Instant.now()).build();
    }

    @Nested @DisplayName("POST /api/v1/queue/enqueue")
    class EnqueueEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 201 with enqueued item")
        void shouldReturn201() throws Exception {
            given(queueService.enqueue(eq(TENANT_ID), any(EnqueueRequest.class)))
                    .willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/queue/enqueue")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobType\":\"DATA_PIPELINE\",\"priority\":3}")
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.jobType", is("DATA_PIPELINE")))
                    .andExpect(jsonPath("$.data.status", is("PENDING")));
        }

        @Test @WithMockUser
        @DisplayName("should return 400 when jobType is blank")
        void shouldReturn400OnBlankJobType() throws Exception {
            mockMvc.perform(post("/api/v1/queue/enqueue")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobType\":\"\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /api/v1/queue/claim")
    class ClaimEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 200 with claimed item")
        void shouldReturn200() throws Exception {
            var claimed = QueueItemResponse.builder()
                    .id(ITEM_ID).tenantId(TENANT_ID).jobType("DATA_PIPELINE")
                    .status("PROCESSING").assignedWorker("worker-001")
                    .createdAt(Instant.now()).build();
            given(queueService.claimNext("worker-001")).willReturn(Optional.of(claimed));

            mockMvc.perform(post("/api/v1/queue/claim")
                            .header(WORKER_HEADER, "worker-001")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assignedWorker", is("worker-001")));
        }

        @Test @WithMockUser
        @DisplayName("should return 204 when no items available")
        void shouldReturn204WhenEmpty() throws Exception {
            given(queueService.claimNext("worker-001")).willReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/queue/claim")
                            .header(WORKER_HEADER, "worker-001")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested @DisplayName("POST /api/v1/queue/{itemId}/complete")
    class CompleteEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 200 on completion")
        void shouldReturn200() throws Exception {
            var completed = QueueItemResponse.builder()
                    .id(ITEM_ID).status("COMPLETED").completedAt(Instant.now()).build();
            given(queueService.complete(ITEM_ID)).willReturn(completed);

            mockMvc.perform(post("/api/v1/queue/{itemId}/complete", ITEM_ID)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("COMPLETED")));
        }
    }

    @Nested @DisplayName("POST /api/v1/queue/{itemId}/fail")
    class FailEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 200 with error message")
        void shouldReturn200() throws Exception {
            var failed = QueueItemResponse.builder()
                    .id(ITEM_ID).status("DEAD_LETTER").errorMessage("timeout").build();
            given(queueService.fail(ITEM_ID, "timeout")).willReturn(failed);

            mockMvc.perform(post("/api/v1/queue/{itemId}/fail", ITEM_ID)
                            .param("errorMessage", "timeout")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.errorMessage", is("timeout")));
        }
    }

    @Nested @DisplayName("POST /api/v1/queue/{itemId}/cancel")
    class CancelEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 204 on cancellation")
        void shouldReturn204() throws Exception {
            mockMvc.perform(post("/api/v1/queue/{itemId}/cancel", ITEM_ID)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested @DisplayName("GET /api/v1/queue/stats")
    class StatsEndpoint {

        @Test @WithMockUser
        @DisplayName("should return queue statistics")
        void shouldReturnStats() throws Exception {
            var stats = QueueStatsResponse.builder()
                    .pendingCount(10).processingCount(5).completedCount(80)
                    .failedCount(3).deadLetterCount(1).cancelledCount(1)
                    .totalCount(100).processingRate(80.0).build();
            given(statsService.getStats(TENANT_ID)).willReturn(stats);

            mockMvc.perform(get("/api/v1/queue/stats")
                            .header(TENANT_HEADER, TENANT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount", is(100)))
                    .andExpect(jsonPath("$.data.processingRate", is(80.0)));
        }
    }
}
