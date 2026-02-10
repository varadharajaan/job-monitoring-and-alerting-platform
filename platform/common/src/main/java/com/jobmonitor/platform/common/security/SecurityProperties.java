package com.jobmonitor.platform.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Security configuration properties — ALL values externalized.
 * Bound from {@code auth.*} prefix in application YAML.
 */
@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
public class SecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();

    @Getter
    @Setter
    public static class Jwt {
        /** JWT signing secret — MUST be injected via env var, never hardcoded */
        private String secret;
        /** Token expiration */
        private Duration expiration = Duration.ofHours(1);
        /** Refresh token expiration */
        private Duration refreshExpiration = Duration.ofDays(1);
        /** JWT issuer claim */
        private String issuer = "job-monitor-platform";
        /** JWT token prefix in Authorization header */
        private String tokenPrefix = "Bearer ";
        /** Authorization header name */
        private String headerName = "Authorization";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("*");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private Duration maxAge = Duration.ofHours(1);
    }
}
