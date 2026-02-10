package com.jobmonitor.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.gateway.dto.IngestionResponse;
import com.jobmonitor.gateway.service.IngestionService;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = IngestionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class}))
@DisplayName("IngestionController Integration Tests")
class IngestionControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private IngestionService ingestionService;

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_ID = "tenant-ingest-int";

    private IngestionResponse sampleResponse() {
        return IngestionResponse.builder()
                .eventId("evt-12345")
                .status("ACCEPTED")
                .receivedAt(Instant.now())
                .build();
    }

    @Nested @DisplayName("POST /api/v1/ingest/events")
    class IngestEventEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 202 Accepted with valid event")
        void shouldReturn202() throws Exception {
            given(ingestionService.ingest(eq(TENANT_ID), any())).willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/ingest/events")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobName\":\"etl-pipeline\",\"eventType\":\"STARTED\"}")
                            .with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.status", is("ACCEPTED")))
                    .andExpect(jsonPath("$.data.eventId", is("evt-12345")));
        }

        @Test @WithMockUser
        @DisplayName("should return 400 when job name is blank")
        void shouldReturn400OnBlankJobName() throws Exception {
            mockMvc.perform(post("/api/v1/ingest/events")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobName\":\"\",\"eventType\":\"STARTED\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test @WithMockUser
        @DisplayName("should return 400 when event type is blank")
        void shouldReturn400OnBlankEventType() throws Exception {
            mockMvc.perform(post("/api/v1/ingest/events")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"jobName\":\"test-job\",\"eventType\":\"\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /api/v1/ingest/batch")
    class IngestBatchEndpoint {

        @Test @WithMockUser
        @DisplayName("should return 202 with batch count message")
        void shouldReturn202ForBatch() throws Exception {
            given(ingestionService.ingest(eq(TENANT_ID), any())).willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/ingest/batch")
                            .header(TENANT_HEADER, TENANT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"jobName\":\"job1\",\"eventType\":\"STARTED\"}," +
                                     "{\"jobName\":\"job2\",\"eventType\":\"COMPLETED\"}]")
                            .with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data", containsString("2 events")));
        }
    }
}
