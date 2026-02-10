package com.jobmonitor.notifworker.service;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.event.NotificationEvent;
import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Notification dispatch service — routes to channel-specific dispatchers.
 * Uses functional dispatch map — each channel is a lambda.
 */
@Service
@Slf4j
public class NotificationDispatchService {

    private final Map<NotificationEvent.Channel, NotificationDispatcher> channelDispatchers;
    private final PlatformProperties platformProperties;

    public NotificationDispatchService(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;

        // Channel dispatcher registry — lambdas, not if-else chains
        this.channelDispatchers = Map.of(
                NotificationEvent.Channel.EMAIL, (recipient, subject, body, metadata) -> {
                    log.info("Dispatching EMAIL to={}, subject={}", recipient, subject);
                    // In production: JavaMailSender.send() or SES SDK
                    return "email-msg-" + System.currentTimeMillis();
                },
                NotificationEvent.Channel.SMS, (recipient, subject, body, metadata) -> {
                    log.info("Dispatching SMS to={}", recipient);
                    // In production: AWS SNS SDK
                    return "sms-msg-" + System.currentTimeMillis();
                },
                NotificationEvent.Channel.SLACK, (recipient, subject, body, metadata) -> {
                    log.info("Dispatching SLACK to channel={}", recipient);
                    // In production: Slack webhook HTTP call
                    return "slack-msg-" + System.currentTimeMillis();
                },
                NotificationEvent.Channel.PUSH, (recipient, subject, body, metadata) -> {
                    log.info("Dispatching PUSH to device={}", recipient);
                    return "push-msg-" + System.currentTimeMillis();
                },
                NotificationEvent.Channel.WEBHOOK, (recipient, subject, body, metadata) -> {
                    log.info("Dispatching WEBHOOK to url={}", recipient);
                    // In production: RestTemplate/WebClient HTTP POST
                    return "webhook-msg-" + System.currentTimeMillis();
                }
        );
    }

    public void dispatch(NotificationEvent event) {
        Optional.ofNullable(event.getChannel())
                .map(channelDispatchers::get)
                .ifPresentOrElse(
                        dispatcher -> {
                            String messageId = dispatcher.dispatch(
                                    event.getRecipient(),
                                    event.getSubject(),
                                    Optional.ofNullable(event.getTemplateVariables())
                                            .map(Object::toString).orElse(""),
                                    Map.of()
                            );
                            log.info("Notification dispatched: channel={}, messageId={}",
                                    event.getChannel(), messageId);
                        },
                        () -> log.warn("No dispatcher for channel: {}", event.getChannel())
                );
    }

    public void handleFailure(NotificationEvent event) {
        log.error("Notification FAILED: id={}, channel={}, reason={}",
                event.getNotificationId(), event.getChannel(), event.getFailureReason());
        // Could implement retry logic or move to DLT
    }

    public void handleBounce(NotificationEvent event) {
        log.warn("Notification BOUNCED: id={}, channel={}, recipient={}",
                event.getNotificationId(), event.getChannel(), event.getRecipient());
    }
}
