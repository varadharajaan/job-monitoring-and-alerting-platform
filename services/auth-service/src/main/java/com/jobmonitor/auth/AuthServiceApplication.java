package com.jobmonitor.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Auth Service — JWT-based authentication and API key management.
 * Provides token generation, validation, and tenant-scoped authorization.
 */
@SpringBootApplication(scanBasePackages = {"com.jobmonitor.auth", "com.jobmonitor.platform.common"})
@ConfigurationPropertiesScan(basePackages = "com.jobmonitor.platform.common.config")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
