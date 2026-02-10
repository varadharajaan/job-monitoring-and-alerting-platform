package com.jobmonitor.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Job Queue Service — persistent background task queue with priority and retry.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Enqueue background jobs with priority and scheduling</li>
 *   <li>Distribute work to workers via Kafka and Redis sorted sets</li>
 *   <li>Track job lifecycle (pending, processing, completed, failed, dead-letter)</li>
 *   <li>Provide queue management APIs and dashboard data</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.queue", "com.jobmonitor.jobqueue", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
@EnableJpaAuditing
@EnableCaching
public class JobQueueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobQueueServiceApplication.class, args);
    }
}
