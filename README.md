# Job Monitoring & Alerting Platform

[![Java 17](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.2.5](https://img.shields.io/badge/Spring_Boot-3.2.5-green)](https://spring.io/projects/spring-boot)
[![Gradle 8.7](https://img.shields.io/badge/Gradle-8.7-02303A)](https://gradle.org/)

A unified, production-grade enterprise platform for **scheduled job monitoring**, **intelligent alerting**, **multi-channel notifications**, and **multi-tenant background job queuing** — built as a Gradle multi-module monorepo with Spring Boot 3.2.5, Kafka 3.7, TimescaleDB, Redis, and **dual-cloud support (AWS + Azure)**.

---

## Architecture

```
+-------------+       +-------------+       +-------------+
|  Dashboard  |       |  CLI / SDK  |       |  External   |
|    (SPA)    |       |   Clients   |       |  Webhooks   |
+------+------+       +------+------+       +------+------+
       |                     |                     |
       +----------+----------+----------+----------+
                  |     HTTPS / JWT     |
       +----------v---------------------v----------+
       |       Ingestion Gateway (:8080)           |
       |    Rate Limiting  |  JWT Validation       |
       +---+-----+-----+-----+-----+-----+--------+
           |     |     |     |     |     |
           v     v     v     v     v     v
       +------+------+------+------+------+--------+
       | Auth | Job  | Ale- | Noti-| Job  | Config |
       | Svc  | Mon  | rtin | fica | Queu | Server |
       |      |      |  g   | tion |  e   |        |
       | 8081 | 8082 | 8083 | 8084 | 8085 |  8888  |
       +------+--+---+--+---+--+---+--+---+--------+
                  |      |      |      |
                  v      v      v      v
       +----------+------+------+------+------------+
       |           Kafka Event Bus                  |
       |        (KRaft mode, 6 topics)              |
       +--------+-----------------------+-----------+
                |                       |
       +--------v--------+    +--------v---------+
       |   Job Worker    |    |  Notif Worker    |
       |   (headless)    |    |   (headless)     |
       +--------+--------+    +--------+---------+
                |                      |
   +------------+----------------------+-------------+
   |                                                 |
   |  +--------------+  +---------+  +--------------+|
   |  | TimescaleDB  |  |  Redis  |  |  LocalStack  ||
   |  |   (pg16)     |  |    7    |  |    (S3)      ||
   |  +--------------+  +---------+  +--------------+|
   |                                                 |
   +-------------------------------------------------+
```

## Modules

This is a Gradle multi-module monorepo with 12 modules:

```
job-monitoring-and-alerting-platform/
+-- buildSrc/                         Shared Gradle conventions
+-- platform/
|   +-- common/                       Shared library (config, exceptions, events, DTOs)
|   +-- schema/                       Flyway SQL migrations (V001-V006)
+-- services/
|   +-- config-server/                Spring Cloud Config (:8888)
|   +-- eureka-server/                Service Discovery Registry (:8761)
|   +-- auth-service/                 JWT authentication (:8081)
|   +-- ingestion-gateway/            API gateway + rate limiting (:8080)
|   +-- job-monitoring-service/       Job registration + execution + SLA + retry (:8082)
|   +-- alerting-service/             Alert rules + Kafka event evaluation (:8083)
|   +-- notification-service/         5-channel notifications + templates (:8084)
|   +-- job-queue-service/            Background job queue + stats (:8085)
+-- workers/
|   +-- job-worker/                   Queue consumer (headless)
|   +-- notification-worker/          Notification delivery (headless)
+-- deploy/docker/                    Docker Compose + Prometheus
+-- docs/                             Architecture, data model, API, deployment
```

## Tech Stack

| Category        | Technology                                            |
|-----------------|-------------------------------------------------------|
| **Runtime**     | Java 17 (language features restricted to Java 11)     |
| **Framework**   | Spring Boot 3.2.5, Spring Cloud 2023.0.1              |
| **Build**       | Gradle 8.7, multi-module with shared conventions      |
| **Messaging**   | Apache Kafka 3.7.0 (KRaft mode, no ZooKeeper)         |
| **Database**    | PostgreSQL 16 + TimescaleDB (hypertables, aggregates) |
| **Cache**       | Redis 7 (5 named caches, rate limiting)               |
| **Cloud**       | AWS SDK 2.25.16, LocalStack (S3 log archive)          |
| **Cloud Alt**   | Azure (Event Hubs, Cache for Redis, PG, Blob)         |
| **Discovery**   | Spring Cloud Netflix Eureka                           |
| **Security**    | jjwt 0.12.5, Bucket4j rate limiting                   |
| **Resilience**  | Resilience4j 2.2.0                                    |
| **Mapping**     | MapStruct 1.5.5                                       |
| **Docs**        | SpringDoc OpenAPI 2.5.0                               |
| **Testing**     | Testcontainers 1.19.7, WireMock                       |
| **Logging**     | Logstash JSON Encoder 7.4, S3 JSONL archive           |
| **Monitoring**  | Prometheus + Grafana                                  |

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose 2.20+
- Git

### 1. Clone & Build

```bash
git clone https://github.com/varadharajaan/job-monitoring-and-alerting-platform.git
cd job-monitoring-and-alerting-platform
./gradlew build -x test
```

### 2. Start Infrastructure

```bash
docker compose -f deploy/docker/docker-compose.yml up -d
```

This starts TimescaleDB (:5432), Kafka (:9092), Redis (:6379), LocalStack (:4566), Prometheus (:9090), and Grafana (:3000).

### 3. Start Services

```bash
# Config server must start first
./gradlew :services:config-server:bootRun

# Eureka server (for service discovery)
./gradlew :services:eureka-server:bootRun

# Then start remaining services (in separate terminals)
./gradlew :services:auth-service:bootRun
./gradlew :services:ingestion-gateway:bootRun
./gradlew :services:job-monitoring-service:bootRun
./gradlew :services:alerting-service:bootRun
./gradlew :services:notification-service:bootRun
./gradlew :services:job-queue-service:bootRun
./gradlew :workers:job-worker:bootRun
./gradlew :workers:notification-worker:bootRun
```

### 4. Verify

```bash
curl http://localhost:8080/actuator/health | jq .
```

### 5. Explore APIs

Open Swagger UI for any service:
- Job Monitoring: http://localhost:8082/swagger-ui.html
- Alerting: http://localhost:8083/swagger-ui.html
- Notifications: http://localhost:8084/swagger-ui.html
- Job Queue: http://localhost:8085/swagger-ui.html

### Run Tests

```bash
./gradlew test
```

## Key Features

| Feature                    | Description                                                   |
|---------------------------|---------------------------------------------------------------|
| **Job Monitoring**        | Cron tracking, SLA enforcement, execution history, retry      |
| **Intelligent Alerting**  | Rule engine with Kafka-driven event evaluation                |
| **Multi-Channel Notify**  | Email, SMS, Slack, Push, Webhook — with templates + rate limit|
| **Background Job Queue**  | Priority queue, locking, retry, dead-letter, stats endpoint   |
| **Service Discovery**     | Netflix Eureka server + client auto-registration              |
| **Azure Cloud Ready**     | Dual-cloud profiles: AWS (default) + Azure (azure profile)    |
| **Multi-Tenancy**         | Tenant isolation via partition key, JWT claim propagation     |
| **Time-Series Analytics** | TimescaleDB hypertables, continuous aggregates, 90d retention |
| **Centralized Config**    | Spring Cloud Config Server with profile-based overrides       |
| **Observability**         | Structured JSON logging, Prometheus metrics, Grafana          |
| **API Gateway**           | Rate limiting (60 RPM), JWT validation, request routing       |
| **S3 Log Archive**        | JSONL logs uploaded to S3 (LocalStack), Athena-queryable      |

## Documentation

| Document                              | Description                                    |
|--------------------------------------|------------------------------------------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design, data flow, ASCII diagrams |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md)     | Database schema, ER diagram, indexes     |
| [docs/API-REFERENCE.md](docs/API-REFERENCE.md) | REST endpoints, error codes, auth      |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)     | Docker setup, env vars, profiles         |
| [PROGRESS.md](PROGRESS.md)                   | Sprint progress and issue tracking       |

## Error Handling

All errors return a consistent `ApiError` response with a centralized `ErrorCode` enum (22 codes). See [docs/API-REFERENCE.md](docs/API-REFERENCE.md#12-error-codes-reference) for the full error code reference.

## Project Status

This project consolidates 5 SaaS product ideas into a single enterprise platform:

1. **Job Monitor SaaS** — Cron monitoring, SLA enforcement, execution tracking
2. **Notification Hub** — Multi-channel template-driven notifications
3. **Query Performance Analyzer** — Slow query detection and optimization (future)
4. **Background Job Queue** — Priority-based distributed task processing
5. **Log Aggregation** — Centralized log collection and search (future)

## License

MIT
