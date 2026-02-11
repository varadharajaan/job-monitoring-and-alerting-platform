# ADR-008: Testcontainers for Integration Testing

**Date:** 2026-01-21  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

Integration tests need real infrastructure (PostgreSQL, Redis, Kafka). Options:
1. **In-memory fakes** — H2, embedded Redis, embedded Kafka
2. **Shared test environment** — dedicated dev/test server
3. **Testcontainers** — disposable Docker containers per test run
4. **Docker Compose** before tests

## Decision

**Testcontainers** with a shared `AbstractIntegrationTest` base class in `platform:common` test fixtures.

## Rationale

- **Production parity:** Tests run against real TimescaleDB (not H2), real Redis, real Kafka — bugs from dialect differences eliminated
- **Isolation:** Each test run gets fresh containers — no flaky state from shared environments
- **`AbstractIntegrationTest`** provides:
  - `PostgreSQLContainer` (timescale/timescaledb:latest-pg16)
  - `GenericContainer` for Redis 7-alpine on port 6379
  - `KafkaContainer` (confluentinc/cp-kafka:7.6.0) in KRaft mode (no ZooKeeper)
  - `@DynamicPropertySource` wiring for Spring Boot auto-configuration
- **Gradle `testFixtures`:** The base class lives in `platform/common/src/testFixtures/` — services declare `testImplementation(testFixtures(project(":platform:common")))` to use it
- **Trade-off:** Tests take ~30s startup (container pull + boot); mitigated by singleton containers (static fields) shared across test classes in a JVM run

## Consequences

- `AbstractIntegrationTest` is `@SpringBootTest` base; service tests extend it
- Containers are static singletons — started once, reused across all test classes in a Gradle test JVM
- First test invocation pulls Docker images (~500MB); subsequent runs reuse cached images
- CI requires Docker-in-Docker or Docker socket access for Testcontainers to work
- `@DynamicPropertySource` overrides `spring.datasource.url`, `spring.data.redis.host/port`, `spring.kafka.bootstrap-servers`
