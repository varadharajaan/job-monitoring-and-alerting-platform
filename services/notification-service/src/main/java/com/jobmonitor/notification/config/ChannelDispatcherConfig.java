package com.jobmonitor.notification.config;

import com.jobmonitor.notification.channel.*;
import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires all notification channel dispatchers into the functional dispatch map
 * consumed by NotificationService.
 */
@Configuration
@RequiredArgsConstructor
public class ChannelDispatcherConfig {

    private final EmailNotificationDispatcher emailDispatcher;
    private final SlackNotificationDispatcher slackDispatcher;
    private final SmsNotificationDispatcher smsDispatcher;
    private final PushNotificationDispatcher pushDispatcher;
    private final WebhookNotificationDispatcher webhookDispatcher;

    @Bean
    public Map<String, NotificationDispatcher> channelDispatchers() {
        return Map.of(
                "EMAIL", emailDispatcher,
                "SLACK", slackDispatcher,
                "SMS", smsDispatcher,
                "PUSH", pushDispatcher,
                "WEBHOOK", webhookDispatcher
        );
    }
}
