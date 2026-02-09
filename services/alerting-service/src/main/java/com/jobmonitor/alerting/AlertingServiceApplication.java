package com.jobmonitor.alerting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Alerting Service — evaluates alert rules against job execution data and
 * triggers multi-channel notifications on SLA violations or failures.
 * <p>
 * Consumes JobEvents from Kafka, evaluates rules, publishes AlertEvents.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.alerting", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableJpaAuditing
@EnableCaching
public class AlertingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertingServiceApplication.class, args);
    }
}