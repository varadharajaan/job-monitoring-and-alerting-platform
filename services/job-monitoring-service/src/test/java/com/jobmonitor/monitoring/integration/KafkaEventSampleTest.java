package com.jobmonitor.monitoring.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobmonitor.platform.common.event.AlertEvent;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.event.NotificationEvent;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sample event serialization/deserialization tests — validates event contracts.
 * These ensure that all platform events can be correctly serialized to JSON
 * for Kafka publishing and deserialized on the consumer side.
 */
@DisplayName("Kafka Event Sample Tests")
class KafkaEventSampleTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final String TENANT_ID = "tenant-kafka-001";

    // ── Event factory functions (functional fixtures) ──

    private static final Supplier<JobEvent> sampleJobEvent = () ->
            JobEvent.builder()
                    .tenantId(TENANT_ID)
                    .jobId(UUID.randomUUID())
                    .jobName("nightly-etl-pipeline")
                    .action(JobEvent.Action.COMPLETED)
                    .cronExpression("0 0 2 * * *")
                    .executionId(UUID.randomUUID())
                    .exitCode(0)
                    .executionStartTime(Instant.now().minusSeconds(3600))
                    .executionEndTime(Instant.now())
                    .build();

    private static final Supplier<AlertEvent> sampleAlertEvent = () ->
            AlertEvent.builder()
                    .tenantId(TENANT_ID)
                    .alertId(UUID.randomUUID())
                    .ruleId(UUID.randomUUID())
                    .action(AlertEvent.Action.TRIGGERED)
                    .severity(AlertEvent.Severity.HIGH)
                    .alertName("sla-breach-detection")
                    .description("Job nightly-etl exceeded SLA of 3600s")
                    .channels(new String[]{"EMAIL", "SLACK"})
                    .context(Map.of("jobName", "nightly-etl", "duration", "4200"))
                    .build();

    private static final Supplier<NotificationEvent> sampleNotificationEvent = () ->
            NotificationEvent.builder()
                    .tenantId(TENANT_ID)
                    .notificationId(UUID.randomUUID())
                    .action(NotificationEvent.Action.SENT)
                    .channel(NotificationEvent.Channel.EMAIL)
                    .recipient("ops-team@example.com")
                    .subject("Alert: SLA Breach — nightly-etl")
                    .templateId("sla-breach-email")
                    .templateVariables(Map.of("jobName", "nightly-etl", "duration", "4200"))
                    .providerMessageId("ses-msg-abc123")
                    .build();

    // ──────────── Serialization Tests ────────────

    @Nested
    @DisplayName("Event Serialization")
    class SerializationTests {

        @Test
        @DisplayName("JobEvent should serialize to valid JSON with all fields")
        void shouldSerializeJobEvent() throws Exception {
            var event = sampleJobEvent.get();
            var json = objectMapper.writeValueAsString(event);

            assertThat(json)
                    .contains("\"eventType\":\"JOB\"")
                    .contains("\"action\":\"COMPLETED\"")
                    .contains("\"jobName\":\"nightly-etl-pipeline\"")
                    .contains("\"exitCode\":0")
                    .contains(TENANT_ID);
        }

        @Test
        @DisplayName("AlertEvent should serialize to valid JSON with context map")
        void shouldSerializeAlertEvent() throws Exception {
            var event = sampleAlertEvent.get();
            var json = objectMapper.writeValueAsString(event);

            assertThat(json)
                    .contains("\"eventType\":\"ALERT\"")
                    .contains("\"action\":\"TRIGGERED\"")
                    .contains("\"severity\":\"HIGH\"")
                    .contains("\"alertName\":\"sla-breach-detection\"")
                    .contains("\"channels\":[\"EMAIL\",\"SLACK\"]")
                    .contains("\"jobName\":\"nightly-etl\"");
        }

        @Test
        @DisplayName("NotificationEvent should serialize to valid JSON")
        void shouldSerializeNotificationEvent() throws Exception {
            var event = sampleNotificationEvent.get();
            var json = objectMapper.writeValueAsString(event);

            assertThat(json)
                    .contains("\"eventType\":\"NOTIFICATION\"")
                    .contains("\"action\":\"SENT\"")
                    .contains("\"channel\":\"EMAIL\"")
                    .contains("\"recipient\":\"ops-team@example.com\"")
                    .contains("\"providerMessageId\":\"ses-msg-abc123\"");
        }
    }

    // ──────────── Deserialization (Round-trip) Tests ────────────

    @Nested
    @DisplayName("Event Deserialization Round-trip")
    class DeserializationTests {

        @Test
        @DisplayName("JobEvent round-trip serialization should preserve all fields")
        void jobEventRoundTrip() throws Exception {
            var original = sampleJobEvent.get();
            var json = objectMapper.writeValueAsString(original);
            var deserialized = objectMapper.readValue(json, JobEvent.class);

            assertThat(deserialized)
                    .satisfies(e -> {
                        assertThat(e.getJobName()).isEqualTo(original.getJobName());
                        assertThat(e.getAction()).isEqualTo(original.getAction());
                        assertThat(e.getExitCode()).isEqualTo(original.getExitCode());
                        assertThat(e.getTenantId()).isEqualTo(TENANT_ID);
                    });
        }

        @Test
        @DisplayName("AlertEvent round-trip serialization should preserve all fields")
        void alertEventRoundTrip() throws Exception {
            var original = sampleAlertEvent.get();
            var json = objectMapper.writeValueAsString(original);
            var deserialized = objectMapper.readValue(json, AlertEvent.class);

            assertThat(deserialized)
                    .satisfies(e -> {
                        assertThat(e.getAlertName()).isEqualTo(original.getAlertName());
                        assertThat(e.getSeverity()).isEqualTo(AlertEvent.Severity.HIGH);
                        assertThat(e.getChannels()).containsExactly("EMAIL", "SLACK");
                        assertThat(e.getContext()).containsEntry("jobName", "nightly-etl");
                    });
        }

        @Test
        @DisplayName("NotificationEvent round-trip serialization should preserve all fields")
        void notificationEventRoundTrip() throws Exception {
            var original = sampleNotificationEvent.get();
            var json = objectMapper.writeValueAsString(original);
            var deserialized = objectMapper.readValue(json, NotificationEvent.class);

            assertThat(deserialized)
                    .satisfies(e -> {
                        assertThat(e.getChannel()).isEqualTo(NotificationEvent.Channel.EMAIL);
                        assertThat(e.getRecipient()).isEqualTo("ops-team@example.com");
                        assertThat(e.getProviderMessageId()).isEqualTo("ses-msg-abc123");
                    });
        }
    }

    // ──────────── Event Contract Validation ────────────

    @Nested
    @DisplayName("Event Contract Validation")
    class EventContractTests {

        @Test
        @DisplayName("All events should have non-null eventId, type, timestamp, and tenantId")
        void allEventsShouldHaveRequiredFields() {
            // Functional approach: validate all events using a single assertion lambda
            Function<Object, String> toJson = event -> {
                try {
                    return objectMapper.writeValueAsString(event);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };

            List.of(sampleJobEvent.get(), sampleAlertEvent.get(), sampleNotificationEvent.get())
                    .stream()
                    .map(toJson)
                    .forEach(json -> assertThat(json)
                            .contains("\"eventId\"")
                            .contains("\"eventType\"")
                            .contains("\"timestamp\"")
                            .contains("\"tenantId\""));
        }

        @Test
        @DisplayName("JobEvent action enum should contain all expected values")
        void jobEventActionsShouldBeComplete() {
            var actions = JobEvent.Action.values();
            assertThat(actions).extracting(Enum::name)
                    .containsExactlyInAnyOrder(
                            "REGISTERED", "STARTED", "COMPLETED", "FAILED",
                            "SLA_VIOLATED", "RETRYING", "HEARTBEAT_MISSED");
        }

        @Test
        @DisplayName("AlertEvent severity enum should contain all expected values")
        void alertSeveritiesShouldBeComplete() {
            var severities = AlertEvent.Severity.values();
            assertThat(severities).extracting(Enum::name)
                    .containsExactlyInAnyOrder(
                            "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
        }

        @Test
        @DisplayName("NotificationEvent channel enum should contain all expected values")
        void notificationChannelsShouldBeComplete() {
            var channels = NotificationEvent.Channel.values();
            assertThat(channels).extracting(Enum::name)
                    .containsExactlyInAnyOrder(
                            "EMAIL", "SMS", "SLACK", "PUSH", "WEBHOOK");
        }
    }
}
