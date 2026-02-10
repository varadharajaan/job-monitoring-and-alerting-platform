package com.jobmonitor.notifworker.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.NotificationEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatchService Unit Tests")
class NotificationDispatchServiceTest {

    @Mock private PlatformProperties platformProperties;

    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new NotificationDispatchService(platformProperties);
    }

    private NotificationEvent createEvent(NotificationEvent.Channel channel, NotificationEvent.Action action) {
        return NotificationEvent.builder()
                .notificationId(UUID.randomUUID())
                .tenantId("tenant-notif-001")
                .channel(channel)
                .action(action)
                .recipient("user@example.com")
                .subject("Test Alert")
                .templateVariables(Map.of("alertName", "test-alert"))
                .build();
    }

    @Nested
    @DisplayName("dispatch()")
    class DispatchTests {

        @Test
        @DisplayName("should dispatch EMAIL notification without error")
        void shouldDispatchEmail() {
            var event = createEvent(NotificationEvent.Channel.EMAIL, NotificationEvent.Action.REQUESTED);

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should dispatch SMS notification without error")
        void shouldDispatchSms() {
            var event = createEvent(NotificationEvent.Channel.SMS, NotificationEvent.Action.REQUESTED);

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should dispatch SLACK notification without error")
        void shouldDispatchSlack() {
            var event = createEvent(NotificationEvent.Channel.SLACK, NotificationEvent.Action.REQUESTED);

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should dispatch PUSH notification without error")
        void shouldDispatchPush() {
            var event = createEvent(NotificationEvent.Channel.PUSH, NotificationEvent.Action.REQUESTED);

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should dispatch WEBHOOK notification without error")
        void shouldDispatchWebhook() {
            var event = createEvent(NotificationEvent.Channel.WEBHOOK, NotificationEvent.Action.REQUESTED);

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle null channel gracefully")
        void shouldHandleNullChannel() {
            var event = NotificationEvent.builder()
                    .notificationId(UUID.randomUUID())
                    .tenantId("tenant-001")
                    .channel(null)
                    .action(NotificationEvent.Action.REQUESTED)
                    .recipient("user@example.com")
                    .build();

            assertThatCode(() -> dispatchService.dispatch(event))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("handleFailure()")
    class HandleFailureTests {

        @Test
        @DisplayName("should handle failure event without error")
        void shouldHandleFailure() {
            var failureEvent = NotificationEvent.builder()
                    .notificationId(UUID.randomUUID())
                    .tenantId("tenant-001")
                    .channel(NotificationEvent.Channel.EMAIL)
                    .action(NotificationEvent.Action.FAILED)
                    .recipient("user@example.com")
                    .failureReason("SMTP connection refused")
                    .build();

            assertThatCode(() -> dispatchService.handleFailure(failureEvent))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("handleBounce()")
    class HandleBounceTests {

        @Test
        @DisplayName("should handle bounce event without error")
        void shouldHandleBounce() {
            var event = createEvent(NotificationEvent.Channel.EMAIL, NotificationEvent.Action.BOUNCED);

            assertThatCode(() -> dispatchService.handleBounce(event))
                    .doesNotThrowAnyException();
        }
    }
}
