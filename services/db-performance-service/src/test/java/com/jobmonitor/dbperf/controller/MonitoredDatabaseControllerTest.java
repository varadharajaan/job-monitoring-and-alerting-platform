package com.jobmonitor.dbperf.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.dbperf.model.MonitoredDatabase;
import com.jobmonitor.dbperf.service.MonitoredDatabaseService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MonitoredDatabaseController.class)
@DisplayName("MonitoredDatabaseController Tests")
class MonitoredDatabaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MonitoredDatabaseService dbService;

    private MonitoredDatabase testDb;

    @BeforeEach
    void setUp() {
        testDb = MonitoredDatabase.builder()
                .id(UUID.randomUUID())
                .tenantId("t1")
                .name("prod-db")
                .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
                .username("admin")
                .encryptedPassword("enc-pass")
                .dbType(MonitoredDatabase.DatabaseType.POSTGRESQL)
                .slowQueryThresholdMs(500)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/databases should register database")
    void shouldRegisterDatabase() throws Exception {
        when(dbService.register(any())).thenReturn(testDb);

        mockMvc.perform(post("/api/v1/databases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDb)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("prod-db"))
                .andExpect(jsonPath("$.tenantId").value("t1"));
    }

    @Test
    @DisplayName("GET /api/v1/databases/{id} should return database")
    void shouldGetById() throws Exception {
        when(dbService.getById(testDb.getId())).thenReturn(Optional.of(testDb));

        mockMvc.perform(get("/api/v1/databases/" + testDb.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("prod-db"));
    }

    @Test
    @DisplayName("GET /api/v1/databases/{id} should return 404 when not found")
    void shouldReturn404() throws Exception {
        when(dbService.getById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/databases/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/databases/tenant/{tenantId} should list databases")
    void shouldListForTenant() throws Exception {
        when(dbService.getForTenant("t1")).thenReturn(List.of(testDb));

        mockMvc.perform(get("/api/v1/databases/tenant/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("prod-db"));
    }
}
