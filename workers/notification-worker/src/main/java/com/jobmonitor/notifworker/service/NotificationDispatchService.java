package com.jobmonitor.notifworker.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.NotificationEvent;
import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Notification dispatch service — routes to channel-specific dispatchers.
 * <p>
 * Each channel is served by a real dispatcher bean wired through
 * {@link com.jobmonitor.notifworker.config.WorkerChannelConfig}.
 * Channels: EMAIL (JavaMailSender), SMS (AWS SNS), SLACK (webhook HTTP),
 * PUSH (FCM HTTP), WEBHOOK (generic HTTP POST).
 */
@Service
@Slf4j
public class NotificationDispatchService {

    private final Map<NotificationEvent.Channel, NotificationDispatcher> channelDispatchers;

    public NotificationDispatchService(
            @Qualifier("workerChannelDispatchers")
            Map<NotificationEvent.Channel, NotificationDispatcher> channelDispatchers) {
        this.channelDispatchers = channelDispatchers;
        log.info("NotificationDispatchService initialized with {} channel dispatchers: {}",
                channelDispatchers.size(), channelDispatchers.keySet());
    }

    public void dispatch(NotificationEvent event) {
        Optional.ofNullable(event.getChannel())
                .map(channelDispatchers::get)
                .ifPresentOrElse(
                        dispatcher -> {
                            try {
                                String body = Optional.ofNullable(event.getTemplateVariables())
                                        .map(Object::toString).orElse("");
                                String messageId = dispatcher.dispatch(
                                        event.getRecipient(),
                                        event.getSubject(),
                                        body,
                                        Map.of()
                                );
                                log.info("Notification dispatched: channel={}, recipient={}, messageId={}",
                                        event.getChannel(), event.getRecipient(), messageId);
                            } catch (Exception e) {
                                log.error("Notification dispatch failed: channel={}, recipient={}, error={}",
                                        event.getChannel(), event.getRecipient(), e.getMessage(), e);
                                throw e;
                            }
                        },
                        () -> log.warn("No dispatcher registered for channel: {}", event.getChannel())
                );
    }

    public void handleFailure(NotificationEvent event) {
        log.error("Notification FAILED: id={}, channel={}, recipient={}, reason={}",
                event.getNotificationId(), event.getChannel(),
                event.getRecipient(), event.getFailureReason());
    }

    public void handleBounce(NotificationEvent event) {
        log.warn("Notification BOUNCED: id={}, channel={}, recipient={}",
                event.getNotificationId(), event.getChannel(), event.getRecipient());
    }
}
