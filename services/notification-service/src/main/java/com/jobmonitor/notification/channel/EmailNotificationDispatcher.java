package com.jobmonitor.notification.channel;

import com.jobmonitor.platform.common.functional.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email notification dispatcher using Spring JavaMailSender.
 * Works with SMTP (local dev), AWS SES, or Azure Communication Services
 * depending on the configured mail properties.
 */
@Slf4j
@Component("emailDispatcher")
@RequiredArgsConstructor
public class EmailNotificationDispatcher implements NotificationDispatcher {

    private final JavaMailSender mailSender;

    @Override
    public String dispatch(String recipient, String subject, String body,
                           java.util.Map<String, String> metadata) {
        try {
            var message = new SimpleMailMessage();
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(metadata.getOrDefault("from", "noreply@jobmonitor.io"));

            mailSender.send(message);

            var providerId = "email-" + java.util.UUID.randomUUID();
            log.info("Email sent to={}, subject='{}', providerId={}", recipient, subject, providerId);
            return providerId;
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Email dispatch failed: " + e.getMessage(), e);
        }
    }
}
