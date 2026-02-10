package com.jobmonitor.platform.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base integration test with shared Testcontainers:
 * <ul>
 *   <li>TimescaleDB (PostgreSQL 16) — used by all JPA services</li>
 *   <li>Redis 7 — used for caching, sessions, distributed locks</li>
 *   <li>Kafka (KRaft) — used for event streaming</li>
 * </ul>
 *
 * <p>Containers are started once per test class hierarchy (shared via static fields)
 * and their connection properties are injected dynamically via {@link DynamicPropertySource}.</p>
 *
 * <p>Usage: extend this class in any {@code @SpringBootTest} integration test.</p>
 *
 * <pre>{@code
 * @SpringBootTest
 * class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     @Test
 *     void shouldPersistData() { ... }
 * }
 * }</pre>
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // ── TimescaleDB (PostgreSQL 16) ─────────────────────────────
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("timescale/timescaledb:latest-pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("jobmonitor_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    // ── Redis 7 ─────────────────────────────────────────────────
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    // ── Kafka (KRaft mode) ──────────────────────────────────────
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withKraft()
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    /**
     * Injects dynamic container connection properties into Spring context.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // ── DataSource ──
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // ── JPA ──
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "true");

        // ── Flyway ──
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");

        // ── Redis ──
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // ── Kafka ──
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // ── JWT (test-only fixed secret for deterministic tests) ──
        registry.add("auth.jwt.secret",
                () -> "test-secret-key-for-integration-tests-min-256-bit-length-padding-here");
        registry.add("auth.jwt.expiration", () -> "PT1H");
    }
}
