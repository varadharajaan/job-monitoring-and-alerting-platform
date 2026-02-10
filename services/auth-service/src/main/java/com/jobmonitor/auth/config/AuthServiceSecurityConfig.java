package com.jobmonitor.auth.config;

import com.jobmonitor.platform.common.config.PlatformProperties;
import com.jobmonitor.platform.common.security.JwtAuthenticationFilter;
import com.jobmonitor.platform.common.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

/**
 * Auth-service specific security — opens login/register endpoints.
 * Overrides the platform default SecurityFilterChain.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class AuthServiceSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PlatformProperties platformProperties;

    @Bean
    @Primary
    public SecurityFilterChain authSecurityFilterChain(HttpSecurity http) throws Exception {
        var publicPaths = Optional.ofNullable(platformProperties.getSecurity())
                .map(PlatformProperties.SecurityConfig::getPublicPaths)
                .orElse(new String[0]);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Auth-service public endpoints — no auth required
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll();
                // Platform public paths
                auth.requestMatchers(publicPaths).permitAll();
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
