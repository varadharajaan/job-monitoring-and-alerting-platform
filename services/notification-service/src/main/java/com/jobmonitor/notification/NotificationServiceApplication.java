package com.jobmonitor.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Notification Service — multi-channel notification dispatch.
 * <p>
 * Channels: Email (SMTP/SES), SMS (SNS), Slack (Webhook), Push, Custom Webhook.
 * Supports templating (Thymeleaf), rate limiting per channel,
 * and delivery tracking with retry.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.notification", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableJpaAuditing
@EnableCaching
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
