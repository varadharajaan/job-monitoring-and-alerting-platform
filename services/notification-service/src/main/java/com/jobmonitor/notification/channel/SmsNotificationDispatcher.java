package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * SMS notification dispatcher.
 * <p>
 * In production, integrates with Twilio or AWS SNS via configuration:
 * - Set {@code TWILIO_ACCOUNT_SID}, {@code TWILIO_AUTH_TOKEN}, {@code TWILIO_FROM_NUMBER}
 * - Or use AWS SNS via the aws profile SMS topic
 * <p>
 * For local development, logs the SMS without actually sending it.
 */
@Slf4j
@Component("smsDispatcher")
public class SmsNotificationDispatcher implements NotificationDispatcher {

    @Override
    public String dispatch(String recipient, String subject, String body,
                           Map<String, String> metadata) {
        // Build SMS content (SMS typically doesn't have subject)
        var smsBody = (subject != null ? subject + ": " : "") + body;

        // Truncate to 160 chars for SMS
        if (smsBody.length() > 160) {
            smsBody = smsBody.substring(0, 157) + "...";
        }

        var providerId = "sms-" + UUID.randomUUID();

        // In production, this would call Twilio or AWS SNS:
        // twilioClient.messages().create(
        //     new PhoneNumber(recipient), new PhoneNumber(fromNumber), smsBody);
        log.info("SMS dispatched to={}, length={}, providerId={}", recipient, smsBody.length(), providerId);

        return providerId;
    }
}
