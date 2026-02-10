package com.jobmonitor.worker.consumer;

import com.jobmonitor.platform.common.event.JobEvent;
import com.jobmonitor.worker.service.JobExecutionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobEventConsumer Unit Tests")
class JobEventConsumerTest {

    @Mock private JobExecutionService jobExecutionService;

    @InjectMocks private JobEventConsumer consumer;

    private static final String TOPIC = "job-monitor.job-events";

    private JobEvent createEvent(JobEvent.Action action) {
        return JobEvent.builder()
                .tenantId("tenant-001")
                .jobName("etl-pipeline")
                .action(action)
                .exitCode(0)
                .build();
    }

    @Nested
    @DisplayName("consume()")
    class ConsumeTests {

        @Test
        @DisplayName("should route REGISTERED action to handleJobRegistered")
        void shouldRouteRegistered() {
            var event = createEvent(JobEvent.Action.REGISTERED);

            consumer.consume(event, TOPIC, 0, 0L);

            then(jobExecutionService).should().handleJobRegistered(event);
        }

        @Test
        @DisplayName("should route STARTED action to handleJobStarted")
        void shouldRouteStarted() {
            var event = createEvent(JobEvent.Action.STARTED);

            consumer.consume(event, TOPIC, 0, 1L);

            then(jobExecutionService).should().handleJobStarted(event);
        }

        @Test
        @DisplayName("should route COMPLETED action to handleJobCompleted")
        void shouldRouteCompleted() {
            var event = createEvent(JobEvent.Action.COMPLETED);

            consumer.consume(event, TOPIC, 0, 2L);

            then(jobExecutionService).should().handleJobCompleted(event);
        }

        @Test
        @DisplayName("should route FAILED action to handleJobFailed")
        void shouldRouteFailed() {
            var event = createEvent(JobEvent.Action.FAILED);

            consumer.consume(event, TOPIC, 0, 3L);

            then(jobExecutionService).should().handleJobFailed(event);
        }

        @Test
        @DisplayName("should route SLA_VIOLATED action to handleSlaViolation")
        void shouldRouteSlaViolated() {
            var event = createEvent(JobEvent.Action.SLA_VIOLATED);

            consumer.consume(event, TOPIC, 0, 4L);

            then(jobExecutionService).should().handleSlaViolation(event);
        }

        @Test
        @DisplayName("should route RETRYING action to handleJobRetry")
        void shouldRouteRetrying() {
            var event = createEvent(JobEvent.Action.RETRYING);

            consumer.consume(event, TOPIC, 0, 5L);

            then(jobExecutionService).should().handleJobRetry(event);
        }

        @Test
        @DisplayName("should route HEARTBEAT_MISSED to handleHeartbeatMissed")
        void shouldRouteHeartbeatMissed() {
            var event = createEvent(JobEvent.Action.HEARTBEAT_MISSED);

            consumer.consume(event, TOPIC, 0, 6L);

            then(jobExecutionService).should().handleHeartbeatMissed(event);
        }

        @Test
        @DisplayName("should propagate exception on handler failure")
        void shouldPropagateException() {
            var event = createEvent(JobEvent.Action.FAILED);
            willThrow(new RuntimeException("Processing error")).given(jobExecutionService).handleJobFailed(event);

            assertThatThrownBy(() -> consumer.consume(event, TOPIC, 0, 7L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Processing error");
        }
    }
}
