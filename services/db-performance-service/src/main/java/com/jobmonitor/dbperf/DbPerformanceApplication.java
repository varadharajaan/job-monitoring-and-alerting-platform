package com.jobmonitor.dbperf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DB Performance Service — slow query monitoring, index suggestions,
 * EXPLAIN ANALYZE visualization, and query cost estimation.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.dbperf", "com.jobmonitor.platform.common"})
@EnableScheduling
public class DbPerformanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbPerformanceApplication.class, args);
    }
}
