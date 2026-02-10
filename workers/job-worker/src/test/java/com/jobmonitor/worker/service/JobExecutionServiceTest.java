package com.jobmonitor.worker.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.AlertEvent;
import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.platform.common.functional.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobExecutionService Unit Tests")
class JobExecutionServiceTest {

    @Mock private EventPublisher eventPublisher;
    @Mock private PlatformProperties platformProperties;

    private JobExecutionService executionService;

    private static final String TENANT_ID = "tenant-worker-001";
    private static final String ALERT_TOPIC = "job-monitor.alert-events";

    @BeforeEach
    void setUp() {
        var kafkaConfig = new PlatformProperties.KafkaConfig();
        var topics = new PlatformProperties.KafkaConfig.Topics();
        topics.setAlertEvents(ALERT_TOPIC);
        kafkaConfig.setTopics(topics);
        lenient().when(platformProperties.getKafka()).thenReturn(kafkaConfig);

        executionService = new JobExecutionService(eventPublisher, platformProperties);
    }

    private JobEvent createEvent(JobEvent.Action action) {
        return JobEvent.builder()
                .tenantId(TENANT_ID)
                .jobName("etl-pipeline")
                .action(action)
                .exitCode(0)
                .build();
    }

    @Nested
    @DisplayName("handleJobRegistered()")
    class HandleRegisteredTests {

        @Test
        @DisplayName("should log registration without publishing alert")
        void shouldLogOnly() {
            var event = createEvent(JobEvent.Action.REGISTERED);

            executionService.handleJobRegistered(event);

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handleJobStarted()")
    class HandleStartedTests {

        @Test
        @DisplayName("should log start without publishing alert")
        void shouldLogOnly() {
            var event = createEvent(JobEvent.Action.STARTED);

            executionService.handleJobStarted(event);

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handleJobCompleted()")
    class HandleCompletedTests {

        @Test
        @DisplayName("should log completion without publishing alert")
        void shouldLogOnly() {
            var event = createEvent(JobEvent.Action.COMPLETED);

            executionService.handleJobCompleted(event);

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handleJobFailed()")
    class HandleFailedTests {

        @Test
        @DisplayName("should publish alert event on job failure")
        void shouldPublishAlert() {
            var event = createEvent(JobEvent.Action.FAILED);
            event = JobEvent.builder()
                    .tenantId(TENANT_ID)
                    .jobName("etl-pipeline")
                    .action(JobEvent.Action.FAILED)
                    .exitCode(1)
                    .errorMessage("Out of memory")
                    .build();

            executionService.handleJobFailed(event);

            then(eventPublisher).should().publish(
                    eq(ALERT_TOPIC),
                    eq(TENANT_ID + ":etl-pipeline"),
                    argThat(evt -> evt instanceof AlertEvent ae &&
                            ae.getAction() == AlertEvent.Action.TRIGGERED &&
                            ae.getSeverity() == AlertEvent.Severity.HIGH));
        }
    }

    @Nested
    @DisplayName("handleSlaViolation()")
    class HandleSlaViolationTests {

        @Test
        @DisplayName("should publish alert event on SLA violation")
        void shouldPublishAlert() {
            var event = createEvent(JobEvent.Action.SLA_VIOLATED);

            executionService.handleSlaViolation(event);

            then(eventPublisher).should().publish(
                    eq(ALERT_TOPIC),
                    eq(TENANT_ID + ":etl-pipeline"),
                    argThat(evt -> evt instanceof AlertEvent ae &&
                            ae.getAction() == AlertEvent.Action.TRIGGERED));
        }
    }

    @Nested
    @DisplayName("handleJobRetry()")
    class HandleRetryTests {

        @Test
        @DisplayName("should log retry without publishing alert")
        void shouldLogOnly() {
            var event = createEvent(JobEvent.Action.RETRYING);

            executionService.handleJobRetry(event);

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("handleHeartbeatMissed()")
    class HandleHeartbeatMissedTests {

        @Test
        @DisplayName("should publish alert event on missed heartbeat")
        void shouldPublishAlert() {
            var event = createEvent(JobEvent.Action.HEARTBEAT_MISSED);

            executionService.handleHeartbeatMissed(event);

            then(eventPublisher).should().publish(
                    eq(ALERT_TOPIC),
                    eq(TENANT_ID + ":etl-pipeline"),
                    argThat(evt -> evt instanceof AlertEvent));
        }
    }
}
