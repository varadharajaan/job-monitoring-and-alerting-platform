package com.jobmonitor.monitoring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing configuration — separated from main application class
 * to avoid "JPA metamodel must not be empty" errors in @WebMvcTest slices.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
