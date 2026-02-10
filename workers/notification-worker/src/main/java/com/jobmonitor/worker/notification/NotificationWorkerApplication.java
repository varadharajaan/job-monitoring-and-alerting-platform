package com.jobmonitor.worker.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Notification Worker — headless worker that delivers notifications.
 * <p>
 * Consumes NotificationEvents from Kafka, resolves templates,
 * dispatches via the appropriate channel (Email, SMS, Slack, Webhook),
 * and reports delivery status back.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.worker.notification", "com.jobmonitor.notifworker", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableJpaAuditing
public class NotificationWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationWorkerApplication.class, args);
    }
}
