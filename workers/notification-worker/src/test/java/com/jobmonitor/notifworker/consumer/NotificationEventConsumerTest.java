package com.jobmonitor.notifworker.consumer;

import com.jobmonitor.notifworker.service.NotificationDispatchService;
import com.jobmonitor.platform.common.event.NotificationEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventConsumer Unit Tests")
class NotificationEventConsumerTest {

    @Mock private NotificationDispatchService dispatchService;

    @InjectMocks private NotificationEventConsumer consumer;

    private static final String TOPIC = "job-monitor.notification-events";

    private NotificationEvent createEvent(NotificationEvent.Action action) {
        return NotificationEvent.builder()
                .notificationId(UUID.randomUUID())
                .tenantId("tenant-001")
                .channel(NotificationEvent.Channel.EMAIL)
                .action(action)
                .recipient("user@example.com")
                .subject("Test Alert")
                .build();
    }

    @Nested
    @DisplayName("consume()")
    class ConsumeTests {

        @Test
        @DisplayName("should route REQUESTED action to dispatch")
        void shouldRouteRequested() {
            var event = createEvent(NotificationEvent.Action.REQUESTED);

            consumer.consume(event, TOPIC, 0, 0L);

            then(dispatchService).should().dispatch(event);
        }

        @Test
        @DisplayName("should route QUEUED action to dispatch")
        void shouldRouteQueued() {
            var event = createEvent(NotificationEvent.Action.QUEUED);

            consumer.consume(event, TOPIC, 0, 1L);

            then(dispatchService).should().dispatch(event);
        }

        @Test
        @DisplayName("should route FAILED action to handleFailure")
        void shouldRouteFailed() {
            var event = createEvent(NotificationEvent.Action.FAILED);

            consumer.consume(event, TOPIC, 0, 2L);

            then(dispatchService).should().handleFailure(event);
        }

        @Test
        @DisplayName("should route BOUNCED action to handleBounce")
        void shouldRouteBounced() {
            var event = createEvent(NotificationEvent.Action.BOUNCED);

            consumer.consume(event, TOPIC, 0, 3L);

            then(dispatchService).should().handleBounce(event);
        }

        @Test
        @DisplayName("should handle SENT action without dispatch call")
        void shouldHandleSent() {
            var event = createEvent(NotificationEvent.Action.SENT);

            assertThatCode(() -> consumer.consume(event, TOPIC, 0, 4L))
                    .doesNotThrowAnyException();

            then(dispatchService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should handle DELIVERED action without dispatch call")
        void shouldHandleDelivered() {
            var event = createEvent(NotificationEvent.Action.DELIVERED);

            assertThatCode(() -> consumer.consume(event, TOPIC, 0, 5L))
                    .doesNotThrowAnyException();

            then(dispatchService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should propagate exception on handler failure")
        void shouldPropagateException() {
            var event = createEvent(NotificationEvent.Action.REQUESTED);
            willThrow(new RuntimeException("Dispatch failed")).given(dispatchService).dispatch(event);

            assertThatThrownBy(() -> consumer.consume(event, TOPIC, 0, 6L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Dispatch failed");
        }
    }
}
