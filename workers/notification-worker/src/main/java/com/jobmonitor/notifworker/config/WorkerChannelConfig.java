package com.jobmonitor.notifworker.config;

import com.jobmonitor.platform.common.event.NotificationEvent;
import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wires real notification channel dispatchers for the notification-worker.
 * <p>
 * Each channel is backed by a real implementation:
 * <ul>
 *   <li>EMAIL — Spring {@link JavaMailSender} (SMTP / AWS SES / MailHog)</li>
 *   <li>SMS — AWS SNS SDK (dry-run when disabled)</li>
 *   <li>SLACK — Incoming Webhook HTTP POST</li>
 *   <li>PUSH — FCM HTTP Legacy API (dry-run when disabled)</li>
 *   <li>WEBHOOK — Generic HTTP POST to recipient URL</li>
 * </ul>
 */
@Configuration
@Slf4j
public class WorkerChannelConfig {

    @Bean("workerChannelDispatchers")
    public Map<NotificationEvent.Channel, NotificationDispatcher> workerChannelDispatchers(
            JavaMailSender mailSender,
            SnsClient snsClient,
            @Value("${notification.email.from:noreply@jobmonitor.local}") String emailFrom,
            @Value("${notification.sms.enabled:false}") boolean smsEnabled,
            @Value("${notification.sms.sender-id:JobMonitor}") String smsSenderId,
            @Value("${notification.slack.webhook-url:}") String slackWebhookUrl,
            @Value("${notification.push.enabled:false}") boolean pushEnabled,
            @Value("${notification.push.fcm-server-key:}") String fcmServerKey,
            @Value("${notification.push.fcm-url:https://fcm.googleapis.com/fcm/send}") String fcmUrl) {

        RestTemplate restTemplate = new RestTemplate();

        // ── EMAIL: JavaMailSender ──
        NotificationDispatcher emailDispatcher = (recipient, subject, body, metadata) -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(body);
                message.setFrom(metadata.getOrDefault("from", emailFrom));
                mailSender.send(message);
                String providerId = "email-" + UUID.randomUUID();
                log.info("Email sent to={}, subject='{}', providerId={}", recipient, subject, providerId);
                return providerId;
            } catch (Exception e) {
                log.error("Failed to send email to={}: {}", recipient, e.getMessage(), e);
                throw new RuntimeException("Email dispatch failed: " + e.getMessage(), e);
            }
        };

        // ── SMS: AWS SNS ──
        NotificationDispatcher smsDispatcher = (recipient, subject, body, metadata) -> {
            String smsBody = (subject != null ? subject + ": " : "") + body;
            if (smsBody.length() > 160) {
                smsBody = smsBody.substring(0, 157) + "...";
            }
            if (!smsEnabled) {
                log.info("[DRY-RUN] SMS to={}, body='{}'", recipient, smsBody);
                return "sms-dryrun-" + System.currentTimeMillis();
            }
            try {
                Map<String, MessageAttributeValue> attrs = new HashMap<>();
                attrs.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                        .stringValue(smsSenderId).dataType("String").build());
                attrs.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                        .stringValue("Transactional").dataType("String").build());
                PublishResponse resp = snsClient.publish(PublishRequest.builder()
                        .message(smsBody).phoneNumber(recipient)
                        .messageAttributes(attrs).build());
                log.info("SMS sent to={}, messageId={}", recipient, resp.messageId());
                return resp.messageId();
            } catch (Exception e) {
                log.error("Failed to send SMS to={}: {}", recipient, e.getMessage(), e);
                throw new RuntimeException("SMS dispatch failed: " + e.getMessage(), e);
            }
        };

        // ── SLACK: Incoming Webhook ──
        NotificationDispatcher slackDispatcher = (recipient, subject, body, metadata) -> {
            if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
                log.warn("Slack webhook URL not configured, skipping to channel={}", recipient);
                return "slack-skipped-" + UUID.randomUUID();
            }
            try {
                String channel = recipient.startsWith("#") ? recipient : "#" + recipient;
                String payload = String.format(
                        "{\"channel\":\"%s\",\"username\":\"JobMonitor\",\"text\":\"*%s*\\n%s\",\"icon_emoji\":\":warning:\"}",
                        escapeJson(channel), escapeJson(subject), escapeJson(body));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                ResponseEntity<String> resp = restTemplate.exchange(
                        slackWebhookUrl, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
                String providerId = "slack-" + UUID.randomUUID();
                log.info("Slack sent to channel={}, status={}, providerId={}", channel, resp.getStatusCode(), providerId);
                return providerId;
            } catch (Exception e) {
                log.error("Slack dispatch failed to={}: {}", recipient, e.getMessage(), e);
                throw new RuntimeException("Slack dispatch failed: " + e.getMessage(), e);
            }
        };

        // ── PUSH: FCM HTTP Legacy API ──
        NotificationDispatcher pushDispatcher = (recipient, subject, body, metadata) -> {
            if (!pushEnabled || fcmServerKey == null || fcmServerKey.isBlank()) {
                log.info("[DRY-RUN] Push to deviceToken={}, subject='{}'",
                        recipient != null && recipient.length() > 12 ? recipient.substring(0, 12) + "..." : recipient, subject);
                return "push-dryrun-" + System.currentTimeMillis();
            }
            try {
                String payload = String.format(
                        "{\"to\":\"%s\",\"notification\":{\"title\":\"%s\",\"body\":\"%s\",\"sound\":\"default\"},\"data\":{\"source\":\"jobmonitor\"},\"priority\":\"high\"}",
                        escapeJson(recipient), escapeJson(subject), escapeJson(body));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "key=" + fcmServerKey);
                ResponseEntity<String> resp = restTemplate.exchange(
                        fcmUrl, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
                String providerId = "push-" + UUID.randomUUID();
                log.info("Push sent to device, status={}, providerId={}", resp.getStatusCode(), providerId);
                return providerId;
            } catch (Exception e) {
                log.error("Push dispatch failed: {}", e.getMessage(), e);
                throw new RuntimeException("Push dispatch failed: " + e.getMessage(), e);
            }
        };

        // ── WEBHOOK: Generic HTTP POST ──
        NotificationDispatcher webhookDispatcher = (recipient, subject, body, metadata) -> {
            try {
                String payload = String.format(
                        "{\"subject\":\"%s\",\"body\":\"%s\",\"timestamp\":\"%s\",\"source\":\"jobmonitor\"}",
                        escapeJson(subject), escapeJson(body), Instant.now().toString());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (metadata.containsKey("authHeader")) {
                    headers.set("Authorization", metadata.get("authHeader"));
                }
                ResponseEntity<String> resp = restTemplate.exchange(
                        recipient, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
                String providerId = "webhook-" + UUID.randomUUID();
                log.info("Webhook sent to={}, status={}, providerId={}", recipient, resp.getStatusCode(), providerId);
                return providerId;
            } catch (Exception e) {
                log.error("Webhook dispatch failed to={}: {}", recipient, e.getMessage(), e);
                throw new RuntimeException("Webhook dispatch failed: " + e.getMessage(), e);
            }
        };

        return Map.of(
                NotificationEvent.Channel.EMAIL, emailDispatcher,
                NotificationEvent.Channel.SMS, smsDispatcher,
                NotificationEvent.Channel.SLACK, slackDispatcher,
                NotificationEvent.Channel.PUSH, pushDispatcher,
                NotificationEvent.Channel.WEBHOOK, webhookDispatcher
        );
    }

    private static String escapeJson(String text) {
        return text == null ? "" : text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
