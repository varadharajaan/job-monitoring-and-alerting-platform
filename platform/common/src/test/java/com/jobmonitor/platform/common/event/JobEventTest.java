package com.jobmonitor.platform.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void builder_setsAllFields() {
        UUID jobId = UUID.randomUUID();
        UUID execId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(60);

        JobEvent event = JobEvent.builder()
                .tenantId("tenant-1")
                .jobId(jobId)
                .executionId(execId)
                .action(JobEvent.Action.COMPLETED)
                .jobName("daily-etl")
                .cronExpression("0 0 2 * * *")
                .exitCode(0)
                .executionStartTime(start)
                .executionEndTime(end)
                .metadata(Map.of("env", "prod"))
                .build();

        assertThat(event.getEventType()).isEqualTo("JOB");
        assertThat(event.getSource()).isEqualTo("job-monitoring-service");
        assertThat(event.getTenantId()).isEqualTo("tenant-1");
        assertThat(event.getJobId()).isEqualTo(jobId);
        assertThat(event.getAction()).isEqualTo(JobEvent.Action.COMPLETED);
        assertThat(event.getJobName()).isEqualTo("daily-etl");
        assertThat(event.getExitCode()).isEqualTo(0);
        assertThat(event.getMetadata()).containsEntry("env", "prod");
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void serialization_roundTrip() throws Exception {
        JobEvent event = JobEvent.builder()
                .tenantId("t1")
                .jobId(UUID.randomUUID())
                .action(JobEvent.Action.FAILED)
                .jobName("broken-job")
                .exitCode(1)
                .errorMessage("OutOfMemory")
                .build();

        String json = objectMapper.writeValueAsString(event);
        assertThat(json).contains("\"eventType\":\"JOB\"");
        assertThat(json).contains("\"action\":\"FAILED\"");
        assertThat(json).contains("\"errorMessage\":\"OutOfMemory\"");

        // Deserialize back via polymorphic type info
        PlatformEvent deserialized = objectMapper.readValue(json, PlatformEvent.class);
        assertThat(deserialized).isInstanceOf(JobEvent.class);
        JobEvent result = (JobEvent) deserialized;
        assertThat(result.getJobName()).isEqualTo("broken-job");
        assertThat(result.getAction()).isEqualTo(JobEvent.Action.FAILED);
    }

    @Test
    void action_enum_hasAllExpectedValues() {
        assertThat(JobEvent.Action.values()).containsExactly(
                JobEvent.Action.REGISTERED,
                JobEvent.Action.STARTED,
                JobEvent.Action.COMPLETED,
                JobEvent.Action.FAILED,
                JobEvent.Action.SLA_VIOLATED,
                JobEvent.Action.RETRYING,
                JobEvent.Action.HEARTBEAT_MISSED
        );
    }

    @Test
    void noArgsConstructor_createsDefaultEvent() {
        JobEvent event = new JobEvent();
        // PlatformEvent base class initializes eventId via field initializer
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getJobId()).isNull();
    }
}
