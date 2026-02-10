package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Generic webhook notification dispatcher.
 * Sends an HTTP POST to the recipient URL with the notification payload as JSON.
 */
@Slf4j
@Component("webhookDispatcher")
public class WebhookNotificationDispatcher implements NotificationDispatcher {

    private final RestTemplate restTemplate;

    public WebhookNotificationDispatcher() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        try {
            var payload = String.format(
                    "{\"subject\":\"%s\",\"body\":\"%s\",\"timestamp\":\"%s\",\"source\":\"jobmonitor\"}",
                    escapeJson(subject),
                    escapeJson(body),
                    java.time.Instant.now().toString());

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Support optional auth header
            if (metadata.containsKey("authHeader")) {
                headers.set("Authorization", metadata.get("authHeader"));
            }

            var request = new HttpEntity<>(payload, headers);
            var response = restTemplate.exchange(recipient, HttpMethod.POST, request, String.class);

            var providerId = "webhook-" + UUID.randomUUID();
            log.info("Webhook sent to={}, status={}, providerId={}", recipient, response.getStatusCode(), providerId);
            return providerId;
        } catch (Exception e) {
            log.error("Failed to send webhook to={}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Webhook dispatch failed: " + e.getMessage(), e);
        }
    }

    private String escapeJson(String text) {
        return text == null ? "" : text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
