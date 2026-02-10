package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * SMS notification dispatcher using AWS SNS.
 * <p>
 * Sends SMS messages via Amazon SNS (Simple Notification Service).
 * When {@code notification.sms.enabled=false} (default for local dev),
 * falls back to logging without actually sending.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code notification.sms.enabled} — enable/disable real sending</li>
 *   <li>{@code notification.sms.sender-id} — SMS sender ID (e.g., "JobMonitor")</li>
 *   <li>AWS credentials configured via standard AWS SDK chain</li>
 * </ul>
 */
@Slf4j
@Component("smsDispatcher")
public class SmsNotificationDispatcher implements NotificationDispatcher {

    private final SnsClient snsClient;
    private final boolean enabled;
    private final String senderId;

    public SmsNotificationDispatcher(
            SnsClient snsClient,
            @Value("${notification.sms.enabled:false}") boolean enabled,
            @Value("${notification.sms.sender-id:JobMonitor}") String senderId) {
        this.snsClient = snsClient;
        this.enabled = enabled;
        this.senderId = senderId;
    }

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        // Build SMS content (SMS typically doesn't have subject)
        var smsBody = (subject != null ? subject + ": " : "") + body;

        // Truncate to 160 chars for SMS
        if (smsBody.length() > 160) {
            smsBody = smsBody.substring(0, 157) + "...";
        }

        if (!enabled) {
            log.info("[DRY-RUN] SMS to={}, length={}, body='{}'", recipient, smsBody.length(), smsBody);
            return "sms-dryrun-" + System.currentTimeMillis();
        }

        try {
            Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();
            smsAttributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                    .stringValue(senderId)
                    .dataType("String")
                    .build());
            smsAttributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                    .stringValue("Transactional")
                    .dataType("String")
                    .build());

            PublishRequest publishRequest = PublishRequest.builder()
                    .message(smsBody)
                    .phoneNumber(recipient)
                    .messageAttributes(smsAttributes)
                    .build();

            PublishResponse response = snsClient.publish(publishRequest);

            String providerId = response.messageId();
            log.info("SMS sent to={}, messageId={}, statusCode={}",
                    recipient, providerId, response.sdkHttpResponse().statusCode());
            return providerId;
        } catch (Exception e) {
            log.error("Failed to send SMS to={}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("SMS dispatch failed: " + e.getMessage(), e);
        }
    }
}
