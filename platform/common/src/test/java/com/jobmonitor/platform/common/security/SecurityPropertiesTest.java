package com.jobmonitor.platform.common.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @Test
    void defaults_areSetCorrectly() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.getJwt()).isNotNull();
        assertThat(props.getJwt().getExpiration()).isEqualTo(Duration.ofHours(1));
        assertThat(props.getJwt().getRefreshExpiration()).isEqualTo(Duration.ofDays(1));
        assertThat(props.getJwt().getIssuer()).isEqualTo("job-monitor-platform");
        assertThat(props.getJwt().getTokenPrefix()).isEqualTo("Bearer ");
        assertThat(props.getJwt().getHeaderName()).isEqualTo("Authorization");
    }

    @Test
    void corsDefaults_areSetCorrectly() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.getCors().getAllowedOrigins()).containsExactly("*");
        assertThat(props.getCors().getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(props.getCors().getAllowedHeaders()).containsExactly("*");
        assertThat(props.getCors().getMaxAge()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void jwtSecret_canBeSet() {
        SecurityProperties props = new SecurityProperties();
        props.getJwt().setSecret("my-secret");
        assertThat(props.getJwt().getSecret()).isEqualTo("my-secret");
    }

    @Test
    void corsOrigins_canBeOverridden() {
        SecurityProperties props = new SecurityProperties();
        props.getCors().setAllowedOrigins(List.of("https://example.com"));
        assertThat(props.getCors().getAllowedOrigins()).containsExactly("https://example.com");
    }
}
