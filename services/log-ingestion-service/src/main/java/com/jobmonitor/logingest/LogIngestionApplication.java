package com.jobmonitor.logingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Log Ingestion Service — real-time log aggregation, Elasticsearch search,
 * pattern alerting, and retention management.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.logingest", "com.jobmonitor.platform.common"})
@EnableScheduling
public class LogIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogIngestionApplication.class, args);
    }
}
