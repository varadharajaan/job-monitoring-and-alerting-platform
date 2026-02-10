package com.jobmonitor.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.config.server.EnableConfigServer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigServerApplication Unit Tests")
class ConfigServerApplicationTest {

    @Test
    @DisplayName("should have @EnableConfigServer annotation")
    void shouldHaveConfigServerAnnotation() {
        assertThat(ConfigServerApplication.class.isAnnotationPresent(EnableConfigServer.class))
                .isTrue();
    }

    @Test
    @DisplayName("should have @SpringBootApplication annotation")
    void shouldHaveSpringBootAnnotation() {
        assertThat(ConfigServerApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .isTrue();
    }
}
