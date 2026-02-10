package com.jobmonitor.eureka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EurekaServerApplication Unit Tests")
class EurekaServerApplicationTest {

    @Test
    @DisplayName("should have @EnableEurekaServer annotation")
    void shouldHaveEurekaServerAnnotation() {
        assertThat(EurekaServerApplication.class.isAnnotationPresent(EnableEurekaServer.class))
                .isTrue();
    }

    @Test
    @DisplayName("should have @SpringBootApplication annotation")
    void shouldHaveSpringBootAnnotation() {
        assertThat(EurekaServerApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .isTrue();
    }
}
