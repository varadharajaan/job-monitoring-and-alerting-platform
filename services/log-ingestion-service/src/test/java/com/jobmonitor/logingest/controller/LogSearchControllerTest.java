package com.jobmonitor.logingest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.logingest.dto.LogSearchRequest;
import com.jobmonitor.logingest.model.LogEntry;
import com.jobmonitor.logingest.service.LogSearchService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogSearchController.class)
@DisplayName("LogSearchController Tests")
class LogSearchControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private LogSearchService logSearchService;

    @Test
    @DisplayName("POST /api/v1/logs/search should return results")
    void shouldSearchLogs() throws Exception {
        var entry = LogEntry.builder()
                .id("log-1")
                .tenantId("t1")
                .level(LogEntry.LogLevel.ERROR)
                .message("NullPointerException")
                .timestamp(Instant.now())
                .build();
        when(logSearchService.search(eq("t1"), eq("NullPointer"), any(), any(), any(), eq(0), eq(50)))
                .thenReturn(List.of(entry));

        var request = LogSearchRequest.builder()
                .tenantId("t1")
                .query("NullPointer")
                .build();

        mockMvc.perform(post("/api/v1/logs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].id").value("log-1"));
    }

    @Test
    @DisplayName("POST /api/v1/logs/ingest should return document ID")
    void shouldIngestLog() throws Exception {
        when(logSearchService.indexLogEntry(any())).thenReturn("doc-123");

        var entry = LogEntry.builder()
                .tenantId("t1")
                .level(LogEntry.LogLevel.INFO)
                .message("Test log")
                .build();

        mockMvc.perform(post("/api/v1/logs/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entry)))
                .andExpect(status().isOk())
                .andExpect(content().string("doc-123"));
    }
}
