package com.jobmonitor.platform.common.test;

import org.junit.jupiter.api.BeforeAll;
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
 * <p><strong>Requires Docker</strong> — tests are automatically skipped when Docker is not available.</p>
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

    protected static volatile boolean containersStarted = false;

    // ── TimescaleDB (PostgreSQL 16) ─────────────────────────────
    protected static PostgreSQLContainer<?> POSTGRES;

    // ── Redis 7 ─────────────────────────────────────────────────
    protected static GenericContainer<?> REDIS;

    // ── Kafka (KRaft mode) ──────────────────────────────────────
    protected static KafkaContainer KAFKA;

    @BeforeAll
    static void startContainers() {
        if (containersStarted) {
            return;
        }

        // Check Docker availability
        boolean dockerAvailable;
        try {
            dockerAvailable = org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(dockerAvailable,
                "Docker is not available — skipping Testcontainers integration tests");

        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("timescale/timescaledb:latest-pg16")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("jobmonitor_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);
        REDIS.start();

        KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                .withKraft()
                .withReuse(true);
        KAFKA.start();

        containersStarted = true;
    }

    /**
     * Injects dynamic container connection properties into Spring context.
     * When Docker is unavailable, containers are null and tests are skipped via {@code @BeforeAll}.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!containersStarted) {
            return; // Tests will be skipped by @BeforeAll assumption
        }

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
