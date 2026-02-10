package com.jobmonitor.notifworker.consumer;

import com.jobmonitor.notifworker.service.NotificationDispatchService;
import com.jobmonitor.platform.common.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Kafka consumer for notification events — dispatches to configured channels.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationDispatchService dispatchService;

    @KafkaListener(
            topics = "${platform.kafka.topics.notification-events:job-monitor.notification-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received notification event: action={}, channel={}, recipient={}, partition={}, offset={}",
                event.getAction(), event.getChannel(), event.getRecipient(), partition, offset);

        try {
            Optional.ofNullable(event.getAction())
                    .map(this::resolveHandler)
                    .ifPresentOrElse(
                            handler -> handler.accept(event),
                            () -> log.warn("Unhandled notification action: {}", event.getAction())
                    );
        } catch (Exception ex) {
            log.error("Failed to process notification event: channel={}, recipient={}, error={}",
                    event.getChannel(), event.getRecipient(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private Consumer<NotificationEvent> resolveHandler(NotificationEvent.Action action) {
        return switch (action) {
            case REQUESTED, QUEUED -> dispatchService::dispatch;
            case SENT -> event -> log.info("Notification sent: id={}", event.getNotificationId());
            case DELIVERED -> event -> log.info("Notification delivered: id={}", event.getNotificationId());
            case FAILED -> dispatchService::handleFailure;
            case BOUNCED -> dispatchService::handleBounce;
        };
    }
}
