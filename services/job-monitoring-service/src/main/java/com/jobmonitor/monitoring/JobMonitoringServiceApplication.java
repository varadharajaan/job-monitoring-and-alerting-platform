package com.jobmonitor.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Job Monitoring Service — tracks scheduled job execution lifecycle.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Job registration with SLA definitions and cron schedules</li>
 *   <li>Execution tracking (start, heartbeat, complete, fail)</li>
 *   <li>SLA evaluation on configurable cron schedule</li>
 *   <li>Dashboard APIs for execution history and statistics</li>
 *   <li>Publishes JobEvents to Kafka for alerting pipeline</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.monitoring", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableCaching
public class JobMonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobMonitoringServiceApplication.class, args);
    }
}
