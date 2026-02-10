package com.jobmonitor.gateway.service;

import com.jobmonitor.gateway.dto.IngestionRequest;
import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService Unit Tests")
class IngestionServiceTest {

    @Mock private EventPublisher eventPublisher;
    @Mock private PlatformProperties platformProperties;

    private IngestionService ingestionService;

    private static final String TENANT_ID = "tenant-ingest-001";
    private static final String JOB_EVENTS_TOPIC = "job-monitor.job-events";

    @BeforeEach
    void setUp() {
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setJobEvents(JOB_EVENTS_TOPIC);
        kafkaConfig.setTopics(topics);
        lenient().when(platformProperties.getKafka()).thenReturn(kafkaConfig);

        ingestionService = new IngestionService(eventPublisher, platformProperties);
    }

    private IngestionRequest createRequest(String eventType) {
        var request = new IngestionRequest();
        request.setJobName("etl-pipeline");
        request.setEventType(eventType);
        request.setExitCode(0);
        request.setMetadata(Map.of("env", "production"));
        return request;
    }

    @Nested
    @DisplayName("ingest()")
    class IngestTests {

        @Test
        @DisplayName("should ingest STARTED event and publish to Kafka")
        void shouldIngestStartedEvent() {
            var request = createRequest("STARTED");

            var result = ingestionService.ingest(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getEventId()).isNotNull();
            assertThat(result.getReceivedAt()).isNotNull();

            then(eventPublisher).should().publish(
                    eq(JOB_EVENTS_TOPIC),
                    eq(TENANT_ID + ":etl-pipeline"),
                    any(JobEvent.class));
        }

        @Test
        @DisplayName("should ingest COMPLETED event")
        void shouldIngestCompletedEvent() {
            var request = createRequest("COMPLETED");

            var result = ingestionService.ingest(TENANT_ID, request);

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            then(eventPublisher).should().publish(eq(JOB_EVENTS_TOPIC), anyString(), argThat(event ->
                    event instanceof JobEvent je && je.getAction() == JobEvent.Action.COMPLETED));
        }

        @Test
        @DisplayName("should ingest FAILED event")
        void shouldIngestFailedEvent() {
            var request = createRequest("FAILED");
            request.setExitCode(1);
            request.setErrorMessage("Out of memory");

            var result = ingestionService.ingest(TENANT_ID, request);

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            then(eventPublisher).should().publish(eq(JOB_EVENTS_TOPIC), anyString(), argThat(event ->
                    event instanceof JobEvent je && je.getAction() == JobEvent.Action.FAILED));
        }

        @Test
        @DisplayName("should map unknown event type to REGISTERED action")
        void shouldMapUnknownToRegistered() {
            var request = createRequest("UNKNOWN_TYPE");

            ingestionService.ingest(TENANT_ID, request);

            then(eventPublisher).should().publish(eq(JOB_EVENTS_TOPIC), anyString(), argThat(event ->
                    event instanceof JobEvent je && je.getAction() == JobEvent.Action.REGISTERED));
        }

        @Test
        @DisplayName("should map HEARTBEAT event to HEARTBEAT_MISSED action")
        void shouldMapHeartbeat() {
            var request = createRequest("HEARTBEAT");

            ingestionService.ingest(TENANT_ID, request);

            then(eventPublisher).should().publish(eq(JOB_EVENTS_TOPIC), anyString(), argThat(event ->
                    event instanceof JobEvent je && je.getAction() == JobEvent.Action.HEARTBEAT_MISSED));
        }

        @Test
        @DisplayName("should handle null event type as REGISTERED")
        void shouldHandleNullEventType() {
            var request = new IngestionRequest();
            request.setJobName("test-job");
            request.setEventType(null);

            ingestionService.ingest(TENANT_ID, request);

            then(eventPublisher).should().publish(eq(JOB_EVENTS_TOPIC), anyString(), argThat(event ->
                    event instanceof JobEvent je && je.getAction() == JobEvent.Action.REGISTERED));
        }
    }
}
