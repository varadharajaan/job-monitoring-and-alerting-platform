package com.jobmonitor.worker.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Job Worker — headless worker (no web server) that processes background jobs.
 * <p>
 * Polls the job queue via Kafka consumer and Redis sorted sets.
 * Acquires distributed locks to prevent duplicate processing.
 * Reports execution status back to job-queue-service via Kafka.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.worker", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableJpaAuditing
public class JobWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobWorkerApplication.class, args);
    }
}
