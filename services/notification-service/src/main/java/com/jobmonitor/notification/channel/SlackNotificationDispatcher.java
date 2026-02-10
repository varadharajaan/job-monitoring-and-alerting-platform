package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Slack notification dispatcher using incoming webhooks.
 * <p>
 * Configure via: {@code notification.slack.webhook-url}
 */
@Slf4j
@Component("slackDispatcher")
public class SlackNotificationDispatcher implements NotificationDispatcher {

    private final String webhookUrl;
    private final RestTemplate restTemplate;

    public SlackNotificationDispatcher(
            @Value("${notification.slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack webhook URL not configured, skipping notification to channel={}", recipient);
            return "slack-skipped-" + UUID.randomUUID();
        }

        try {
            var channel = recipient.startsWith("#") ? recipient : "#" + recipient;
            var payload = String.format(
                    "{\"channel\":\"%s\",\"username\":\"JobMonitor\",\"text\":\"*%s*\\n%s\",\"icon_emoji\":\":warning:\"}",
                    channel,
                    escapeJson(subject),
                    escapeJson(body));

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            var request = new HttpEntity<>(payload, headers);

            var response = restTemplate.exchange(webhookUrl, HttpMethod.POST, request, String.class);

            var providerId = "slack-" + UUID.randomUUID();
            log.info("Slack message sent to channel={}, status={}, providerId={}",
                    channel, response.getStatusCode(), providerId);
            return providerId;
        } catch (Exception e) {
            log.error("Failed to send Slack notification to={}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Slack dispatch failed: " + e.getMessage(), e);
        }
    }

    private String escapeJson(String text) {
        return text == null ? "" : text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
