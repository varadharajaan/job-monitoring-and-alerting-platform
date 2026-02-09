package com.jobmonitor.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ingestion Gateway — API entry point for all external clients.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Request routing to downstream services</li>
 *   <li>Rate limiting (token bucket per tenant)</li>
 *   <li>Request validation and sanitization</li>
 *   <li>API key / JWT authentication delegation</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.gateway", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
public class IngestionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionGatewayApplication.class, args);
    }
}
