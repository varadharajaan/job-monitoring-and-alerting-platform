# Job Monitoring & Alerting Platform

A unified, production-grade platform for **scheduled job monitoring**, **multi-channel notifications**, **database query performance analysis**, and **multi-tenant background job queuing** — built with Spring Boot 3.x, Kafka, TimescaleDB, and Redis.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway / REST Controllers                │
├──────────┬──────────┬─────────────┬─────────────────────────────┤
│  Job     │ Notif-   │ Query       │ Background                  │
│  Monitor │ ication  │ Analyzer    │ Job Queue                   │
├──────────┴──────────┴─────────────┴─────────────────────────────┤
│               Kafka Event Bus (Async Communication)             │
├─────────────────────────────────────────────────────────────────┤
│    Redis (Caching, Rate Limiting)  │  TimescaleDB (Persistence) │
├─────────────────────────────────────────────────────────────────┤
│         S3 (Log Archive)  │  Elasticsearch (Future: Logs)       │
└─────────────────────────────────────────────────────────────────┘
```

## Modules

| Module | Description | Status |
|--------|-------------|--------|
| **Job Monitor** | Cron monitoring, execution history, SLA alerts, retry | ✅ Active |
| **Notification** | Email, SMS, Slack, Push — template-driven, rate-limited | ✅ Active |
| **Query Analyzer** | Slow query detection, index suggestions, explain plans | ✅ Active |
| **Job Queue** | Multi-tenant background job processing with priorities | ✅ Active |
| **Log Aggregation** | Real-time log collection & search | 🔮 Future |

## Tech Stack

- **Runtime**: Java 17, Spring Boot 3.2.x
- **Messaging**: Apache Kafka
- **Database**: PostgreSQL + TimescaleDB extension
- **Cache**: Redis
- **Logging**: Logback → JSON (JSONL) → S3 → Athena
- **Build**: Maven
- **Container**: Docker + Docker Compose
- **CI/CD**: GitHub Actions

## Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Maven 3.9+

### Run Locally

```bash
# Start infrastructure
docker compose -f docker/docker-compose.yml up -d

# Build & run
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Run Tests

```bash
./mvnw test
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/jobs` | Register a monitored job |
| `GET` | `/api/v1/jobs` | List all monitored jobs |
| `POST` | `/api/v1/jobs/{id}/executions` | Record job execution |
| `POST` | `/api/v1/jobs/{id}/retry` | Retry a failed job |
| `POST` | `/api/v1/notifications/send` | Send a notification |
| `GET` | `/api/v1/notifications/templates` | List notification templates |
| `POST` | `/api/v1/queries/analyze` | Analyze a SQL query |
| `GET` | `/api/v1/queries/slow` | List slow queries |
| `POST` | `/api/v1/queue/jobs` | Submit a background job |
| `GET` | `/api/v1/queue/jobs` | List queued jobs |

## Configuration

All secrets and configuration are centralized via Spring profiles and environment variables. See `application.yml` for defaults.

| Property | Description | Default |
|----------|-------------|---------|
| `platform.kafka.bootstrap-servers` | Kafka brokers | `localhost:9092` |
| `platform.redis.host` | Redis host | `localhost` |
| `platform.s3.bucket` | S3 bucket for log archiving | `job-monitor-logs` |
| `platform.notification.rate-limit` | Max notifications/min/channel | `100` |

## License

MIT
