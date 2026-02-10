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
 * Push notification dispatcher using Firebase Cloud Messaging (FCM) HTTP v1 API.
 * <p>
 * Sends push notifications to mobile/web devices via FCM.
 * When {@code notification.push.enabled=false} (default for local dev),
 * falls back to logging without actually sending.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code notification.push.enabled} — enable/disable real sending</li>
 *   <li>{@code notification.push.fcm-server-key} — FCM server key for legacy HTTP API</li>
 *   <li>{@code notification.push.fcm-url} — FCM API endpoint (defaults to Google's FCM endpoint)</li>
 * </ul>
 */
@Slf4j
@Component("pushDispatcher")
public class PushNotificationDispatcher implements NotificationDispatcher {

    private static final String DEFAULT_FCM_URL = "https://fcm.googleapis.com/fcm/send";

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String fcmServerKey;
    private final String fcmUrl;

    public PushNotificationDispatcher(
            @Value("${notification.push.enabled:false}") boolean enabled,
            @Value("${notification.push.fcm-server-key:}") String fcmServerKey,
            @Value("${notification.push.fcm-url:" + DEFAULT_FCM_URL + "}") String fcmUrl) {
        this.restTemplate = new RestTemplate();
        this.enabled = enabled;
        this.fcmServerKey = fcmServerKey;
        this.fcmUrl = fcmUrl;
    }

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        if (!enabled || fcmServerKey == null || fcmServerKey.isBlank()) {
            log.info("[DRY-RUN] Push notification to deviceToken={}, subject='{}'",
                    truncateToken(recipient), subject);
            return "push-dryrun-" + System.currentTimeMillis();
        }

        try {
            String payload = buildFcmPayload(recipient, subject, body, metadata);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "key=" + fcmServerKey);

            HttpEntity<String> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    fcmUrl, HttpMethod.POST, request, String.class);

            String providerId = "push-" + UUID.randomUUID();
            log.info("Push notification sent to deviceToken={}, status={}, providerId={}",
                    truncateToken(recipient), response.getStatusCode(), providerId);
            return providerId;
        } catch (Exception e) {
            log.error("Failed to send push notification to deviceToken={}: {}",
                    truncateToken(recipient), e.getMessage(), e);
            throw new RuntimeException("Push notification dispatch failed: " + e.getMessage(), e);
        }
    }

    private String buildFcmPayload(String deviceToken, String title, String body,
                                    Map<String, String> metadata) {
        var dataEntries = new StringBuilder();
        if (metadata != null && !metadata.isEmpty()) {
            metadata.forEach((key, value) ->
                    dataEntries.append(String.format(",\"%s\":\"%s\"", escapeJson(key), escapeJson(value))));
        }

        return String.format(
                "{\"to\":\"%s\",\"notification\":{\"title\":\"%s\",\"body\":\"%s\",\"sound\":\"default\"},\"data\":{\"source\":\"jobmonitor\"%s},\"priority\":\"high\"}",
                escapeJson(deviceToken),
                escapeJson(title),
                escapeJson(body),
                dataEntries);
    }

    private String truncateToken(String token) {
        if (token != null && token.length() > 12) {
            return token.substring(0, 12) + "...";
        }
        return token;
    }

    private String escapeJson(String text) {
        return text == null ? "" : text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
