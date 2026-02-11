package com.jobmonitor.monitoring.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmonitor.platform.common.security.JwtTokenProvider;
import com.jobmonitor.platform.common.test.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the Job Monitoring Service.
 *
 * <p>Exercises the FULL stack: HTTP → Controller → Service → Repository → TimescaleDB
 * with real Testcontainers, JWT authentication, and Kafka event verification.</p>
 *
 * <p>Lifecycle tested: Register Job → Record Execution → Query → Deactivate → Verify Kafka Events</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Job Lifecycle E2E Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobLifecycleE2ETest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static String tenantId;
    private static String jwtToken;
    private static String jobId;

    @BeforeAll
    static void seedTenant(@Autowired JdbcTemplate jdbc, @Autowired JwtTokenProvider tokenProvider) {
        tenantId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO tenants (id, name, slug, plan, status) VALUES (?::uuid, ?, ?, 'FREE', 'ACTIVE')",
                tenantId, "e2e-tenant-" + tenantId.substring(0, 8), "e2e-" + tenantId.substring(0, 8)
        );

        // Generate a valid JWT for all requests
        jwtToken = tokenProvider.generateToken(
                UUID.randomUUID().toString(), "e2e-user", tenantId, List.of("ADMIN"));
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Register a new job via POST /api/v1/jobs")
    void registerJob() throws Exception {
        var jobRequest = Map.of(
                "name", "e2e-nightly-etl",
                "description", "End-to-end test job for ETL pipeline",
                "cronExpression", "0 0 2 * * *",
                "scheduleType", "CRON",
                "slaSeconds", 3600,
                "tags", List.of("e2e", "etl", "nightly"),
                "metadata", Map.of("env", "test", "team", "platform")
        );

        MvcResult result = mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jobRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("e2e-nightly-etl")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.tags", hasSize(3)))
                .andExpect(jsonPath("$.data.metadata.env", is("test")))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        // Extract job ID for subsequent tests
        var responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        jobId = responseBody.path("data").path("id").asText();
        assertThat(jobId).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Record a STARTED execution via POST /api/v1/jobs/{id}/executions")
    void recordStartedExecution() throws Exception {
        var executionRequest = Map.of(
                "status", "RUNNING",
                "startedAt", Instant.now().toString()
        );

        mockMvc.perform(post("/api/v1/jobs/{jobId}/executions", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(executionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("RUNNING")));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Record a COMPLETED execution")
    void recordCompletedExecution() throws Exception {
        var executionRequest = Map.of(
                "status", "SUCCESS",
                "startedAt", Instant.now().minusSeconds(120).toString(),
                "completedAt", Instant.now().toString(),
                "durationMs", 120000,
                "exitCode", 0
        );

        mockMvc.perform(post("/api/v1/jobs/{jobId}/executions", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(executionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.exitCode", is(0)));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Query single job by ID via GET /api/v1/jobs/{id}")
    void getJobById() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(jobId)))
                .andExpect(jsonPath("$.data.name", is("e2e-nightly-etl")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: List jobs with pagination via GET /api/v1/jobs")
    void listJobs() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: Duplicate job name returns 409 Conflict")
    void duplicateJobReturns409() throws Exception {
        var duplicateRequest = Map.of(
                "name", "e2e-nightly-etl",
                "scheduleType", "CRON"
        );

        mockMvc.perform(post("/api/v1/jobs")
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    @DisplayName("Step 7: Get job executions via GET /api/v1/jobs/{id}/executions")
    void getJobExecutions() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}/executions", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(8)
    @DisplayName("Step 8: Deactivate job via DELETE /api/v1/jobs/{id}")
    void deactivateJob() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("deactivated")));

        // Verify job is now INACTIVE
        mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));
    }

    @Test
    @Order(9)
    @DisplayName("Step 9: Non-existent job returns 404")
    void nonExistentJobReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/{jobId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(10)
    @DisplayName("Step 10: Unauthenticated request returns 401/403")
    void unauthenticatedRequestFails() throws Exception {
        mockMvc.perform(get("/api/v1/jobs")
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(11)
    @DisplayName("Step 11: Verify Kafka events were published during lifecycle")
    void verifyKafkaEvents() throws Exception {
        if (!containersStarted || KAFKA == null) {
            return; // Skip if no Docker
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-verify-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("job-monitor.job-events"));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            // We expect at minimum: REGISTERED, execution events, deactivated
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            // Verify events contain our tenant ID
            boolean foundOurEvent = false;
            for (var record : records) {
                if (record.value().contains(tenantId)) {
                    foundOurEvent = true;
                    break;
                }
            }
            assertThat(foundOurEvent).as("Should find Kafka event for our tenant").isTrue();
        }
    }
}
