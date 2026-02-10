# Deployment Guide

> **Job Monitoring & Alerting Platform** — Local, Docker, and Production Deployment  
> Last updated: 2026-02-10

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Local Development Setup](#2-local-development-setup)
3. [Docker Compose Stack](#3-docker-compose-stack)
4. [Service Startup Order](#4-service-startup-order)
5. [Environment Variables Reference](#5-environment-variables-reference)
6. [Spring Profiles](#6-spring-profiles)
7. [Health Checks & Verification](#7-health-checks--verification)
8. [Production Considerations](#8-production-considerations)
9. [Monitoring Setup](#9-monitoring-setup)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

| Tool               | Version   | Purpose                          |
|-------------------|-----------|----------------------------------|
| Java (JDK)        | 17+       | Build and runtime                |
| Gradle            | 8.7       | Build tool (wrapper included)    |
| Docker            | 24+       | Infrastructure containers        |
| Docker Compose    | 2.20+     | Multi-container orchestration    |
| Git               | 2.40+     | Source control                   |
| gh CLI (optional) | 2.40+     | GitHub issue/PR management       |

---

## 2. Local Development Setup

### 2.1 Clone and Build

```bash
git clone https://github.com/varadharajaan/job-monitoring-and-alerting-platform.git
cd job-monitoring-and-alerting-platform

# Build all modules (skip tests for speed)
./gradlew build -x test

# Run tests
./gradlew test
```

### 2.2 Start Infrastructure

```bash
docker compose -f deploy/docker/docker-compose.yml up -d
```

This starts:

```
+-------------------+--------+-----------------------------------+
| Container         | Port   | Status Check                      |
+-------------------+--------+-----------------------------------+
| TimescaleDB       | 5432   | pg_isready -U jobmonitor          |
| Kafka (KRaft)     | 9092   | kafka-broker-api-versions.sh      |
| Redis             | 6379   | redis-cli -a redis_secret ping    |
| LocalStack (S3)   | 4566   | curl http://localhost:4566        |
| Eureka Server     | 8761   | curl http://localhost:8761/actuator/health |
| Prometheus        | 9090   | http://localhost:9090/-/ready     |
| Grafana           | 3000   | http://localhost:3000 (admin/admin)|
+-------------------+--------+-----------------------------------+
```

### 2.3 Create S3 Bucket (LocalStack)

```bash
aws --endpoint-url=http://localhost:4566 s3 mb s3://job-monitor-logs
```

### 2.4 Start Services

```bash
# Terminal 1: Config Server (start first, other services depend on it)
./gradlew :services:config-server:bootRun

# Terminal 2: Eureka Server (start before other services for discovery)
./gradlew :services:eureka-server:bootRun

# Terminal 3: Auth Service
./gradlew :services:auth-service:bootRun

# Terminal 3: Ingestion Gateway
./gradlew :services:ingestion-gateway:bootRun

# Terminal 4: Job Monitoring Service
./gradlew :services:job-monitoring-service:bootRun

# Terminal 5: Alerting Service
./gradlew :services:alerting-service:bootRun

# Terminal 6: Notification Service
./gradlew :services:notification-service:bootRun

# Terminal 7: Job Queue Service
./gradlew :services:job-queue-service:bootRun

# Terminal 8: Job Worker
./gradlew :workers:job-worker:bootRun

# Terminal 9: Notification Worker
./gradlew :workers:notification-worker:bootRun
```

---

## 3. Docker Compose Stack

```
deploy/docker/docker-compose.yml
deploy/docker/prometheus.yml
```

### 3.1 Architecture

```
+------------------------------------------------------------------+
|                    Docker Compose Network                         |
+------------------------------------------------------------------+
|                                                                  |
|  +-------------------+    +-------------------+                  |
|  | jobmonitor-       |    | jobmonitor-kafka  |                  |
|  | postgres          |    | (KRaft mode)      |                  |
|  | :5432             |    | :9092             |                  |
|  | timescale/        |    | apache/kafka:3.7.0|                  |
|  | timescaledb:pg16  |    |                   |                  |
|  +-------------------+    +-------------------+                  |
|                                                                  |
|  +-------------------+    +-------------------+                  |
|  | jobmonitor-redis  |    | jobmonitor-       |                  |
|  | :6379             |    | localstack        |                  |
|  | redis:7-alpine    |    | :4566 (S3)        |                  |
|  | 256mb / LRU       |    | localstack/3      |                  |
|  +-------------------+    +-------------------+                  |
|                                                                  |
|  +-------------------+    +-------------------+                  |
|  | jobmonitor-       |    | jobmonitor-       |                  |
|  | prometheus        |    | grafana           |                  |
|  | :9090             |    | :3000             |                  |
|  | prom/prometheus   |    | grafana/grafana   |                  |
|  +-------------------+    +-------------------+                  |
|                                                                  |
+------------------------------------------------------------------+
```

### 3.2 Volumes

| Volume           | Purpose                     |
|-----------------|-----------------------------|
| `postgres_data` | Database persistence         |
| `redis_data`    | Redis AOF/RDB persistence    |
| `localstack_data`| S3 bucket data             |
| `grafana_data`  | Grafana dashboards + config  |

### 3.3 Commands

```bash
# Start all infrastructure
docker compose -f deploy/docker/docker-compose.yml up -d

# View logs
docker compose -f deploy/docker/docker-compose.yml logs -f kafka

# Stop all
docker compose -f deploy/docker/docker-compose.yml down

# Stop and destroy volumes (clean reset)
docker compose -f deploy/docker/docker-compose.yml down -v
```

---

## 4. Service Startup Order

Services must be started in dependency order:

```
Phase 1 (Infrastructure):        Phase 2 (Platform):
+-------------------+            +-------------------+
| TimescaleDB       |            | config-server     |
| Kafka             |  ------>   | :8888             |
| Redis             |            +-------------------+
| LocalStack        |                    |
+-------------------+                    v
                                 Phase 2.5 (Discovery):
                                 +-------------------+
                                 | eureka-server     |
                                 | :8761             |
                                 +-------------------+
                                         |
                                         v
                                 Phase 3 (Auth):
                                 +-------------------+
                                 | auth-service      |
                                 | :8081             |
                                 +-------------------+
                                         |
                                         v
                                 Phase 4 (Gateway):
                                 +-------------------+
                                 | ingestion-gateway |
                                 | :8080             |
                                 +-------------------+
                                         |
                                         v
                                 Phase 5 (Services):
                    +----------+---------+----------+----------+
                    |          |         |          |          |
                    v          v         v          v          v
              +---------+ +--------+ +--------+ +--------+ +--------+
              | job-mon | | alert  | | notif  | | queue  | | workers|
              | :8082   | | :8083  | | :8084  | | :8085  | | (2x)   |
              +---------+ +--------+ +--------+ +--------+ +--------+
```

---

## 5. Environment Variables Reference

### 5.1 Database

| Variable         | Default                                          | Description        |
|-----------------|--------------------------------------------------|--------------------|
| `DB_URL`        | `jdbc:postgresql://localhost:5432/jobmonitor`    | JDBC URL           |
| `DB_USERNAME`   | `jobmonitor`                                     | DB user            |
| `DB_PASSWORD`   | `jobmonitor_secret`                              | DB password        |
| `DB_POOL_SIZE`  | `10`-`15` (varies by service)                    | HikariCP max pool  |
| `DB_POOL_MIN_IDLE` | `2`-`3`                                       | HikariCP min idle  |
| `DB_CONN_TIMEOUT` | `30000`                                        | Connection timeout |

### 5.2 Kafka

| Variable             | Default                | Description                |
|---------------------|------------------------|----------------------------|
| `KAFKA_BOOTSTRAP`   | `localhost:9092`       | Kafka broker addresses     |
| `KAFKA_GROUP_*`     | `{service}-group`      | Consumer group ID          |

### 5.3 Redis

| Variable         | Default         | Description        |
|-----------------|-----------------|---------------------|
| `REDIS_HOST`    | `localhost`     | Redis host          |
| `REDIS_PORT`    | `6379`          | Redis port          |
| `REDIS_PASSWORD`| `redis_secret`  | Redis auth password |

### 5.4 Auth / JWT

| Variable               | Default                            | Description         |
|-----------------------|-------------------------------------|---------------------|
| `JWT_SECRET`          | `change-me-in-production-...`      | HMAC signing key    |
| `JWT_EXPIRATION`      | `3600000` (1 hour)                 | Access token TTL    |
| `JWT_REFRESH_EXPIRATION` | `86400000` (24 hours)           | Refresh token TTL   |
| `JWT_ISSUER`          | `job-monitor-platform`             | JWT issuer claim    |

### 5.5 Notification

| Variable                | Default                        | Description            |
|------------------------|--------------------------------|------------------------|
| `SMTP_HOST`            | `localhost`                    | SMTP server            |
| `SMTP_PORT`            | `1025`                         | SMTP port              |
| `SMTP_USER`            |                                | SMTP username          |
| `SMTP_PASSWORD`        |                                | SMTP password          |
| `SMTP_AUTH`            | `false`                        | Enable SMTP auth       |
| `SMTP_TLS`             | `false`                        | Enable STARTTLS        |
| `SLACK_WEBHOOK_URL`    |                                | Slack incoming webhook |
| `SLACK_DEFAULT_CHANNEL`| `#alerts`                      | Default Slack channel  |
| `EMAIL_FROM`           | `noreply@jobmonitor.local`     | Sender email           |
| `EMAIL_REPLY_TO`       | `support@jobmonitor.local`     | Reply-to email         |

### 5.6 S3 / Cloud

| Variable               | Default                | Description          |
|-----------------------|------------------------|----------------------|
| `platform.s3.enabled` | `false`                | Enable S3 log upload |
| `platform.s3.bucket`  | `job-monitor-logs`     | S3 bucket name       |
| `platform.s3.region`  | `us-east-1`            | AWS region           |
| `platform.s3.endpoint`|                        | LocalStack endpoint  |

### 5.7 Service Ports

| Variable              | Default | Service                |
|----------------------|---------|------------------------|
| `CONFIG_SERVER_PORT` | `8888`  | config-server          |
| `AUTH_SERVICE_PORT`  | `8081`  | auth-service           |
| `GATEWAY_PORT`       | `8080`  | ingestion-gateway      |
| `JOB_MONITOR_PORT`   | `8082`  | job-monitoring-service |
| `ALERTING_PORT`      | `8083`  | alerting-service       |
| `NOTIFICATION_PORT`  | `8084`  | notification-service   |
| `JOB_QUEUE_PORT`     | `8085`  | job-queue-service      |

### 5.8 Rate Limiting

| Variable             | Default | Description              |
|---------------------|---------|--------------------------|
| `RATE_LIMIT_ENABLED`| `true`  | Enable rate limiting     |
| `RATE_LIMIT_RPM`    | `60`    | Requests per minute      |
| `RATE_LIMIT_BURST`  | `10`    | Burst capacity           |

### 5.9 Eureka Service Discovery

| Variable              | Default                                    | Description              |
|----------------------|---------------------------------------------|--------------------------|
| `EUREKA_ENABLED`     | `false`                                     | Enable Eureka client     |
| `EUREKA_URL`         | `http://localhost:8761/eureka`              | Eureka server URL        |
| `EUREKA_USER`        | `eureka`                                    | Eureka HTTP Basic user   |
| `EUREKA_PASSWORD`    | `eureka_secret`                             | Eureka HTTP Basic pass   |

### 5.10 Azure Cloud (azure profile)

| Variable                           | Default | Description                          |
|-----------------------------------|---------|--------------------------------------|
| `AZURE_DB_URL`                    |         | Azure PG JDBC URL (?sslmode=require) |
| `AZURE_DB_USERNAME`               |         | Azure PG username                    |
| `AZURE_DB_PASSWORD`               |         | Azure PG password                    |
| `AZURE_EVENTHUBS_BOOTSTRAP`       |         | Event Hubs Kafka endpoint            |
| `AZURE_EVENTHUBS_CONNECTION_STRING`|        | Event Hubs shared access policy      |
| `AZURE_REDIS_HOST`                |         | Azure Cache for Redis hostname       |
| `AZURE_REDIS_PORT`                | `6380`  | Azure Redis SSL port                 |
| `AZURE_REDIS_PASSWORD`            |         | Azure Redis access key               |
| `AZURE_BLOB_ENABLED`              | `false` | Enable Azure Blob Storage            |
| `AZURE_STORAGE_CONNECTION_STRING` |         | Azure Storage account conn string    |
| `AZURE_LOG_CONTAINER`             | `logs`  | Blob container for log uploads       |

---

## 6. Spring Profiles

| Profile   | Description                                      | Usage                       |
|----------|--------------------------------------------------|-----------------------------|
| `local`  | Default. Debug logging, SQL logging, LocalStack  | `SPRING_PROFILES_ACTIVE=local` |
| `azure`  | Azure cloud: Event Hubs, Azure Redis, Azure PG   | `SPRING_PROFILES_ACTIVE=azure` |
| `prod`   | Warn logging, higher pool sizes, real AWS        | `SPRING_PROFILES_ACTIVE=prod`  |
| `native` | Config server file-based config                  | config-server only          |

### Profile-specific overrides (job-monitoring-service example):

```
application.yml             Base config (all profiles)
application-local.yml       show-sql=true, DEBUG logging, LocalStack S3
application-prod.yml        show-sql=false, WARN logging, pool=30, replicas=3
```

---

## 7. Health Checks & Verification

After starting all services, verify the stack:

```bash
# Infrastructure
docker compose -f deploy/docker/docker-compose.yml ps

# Service health checks
curl -s http://localhost:8888/actuator/health | jq .  # config-server
curl -s http://localhost:8761/actuator/health | jq .  # eureka-server
curl -s http://localhost:8081/actuator/health | jq .  # auth-service
curl -s http://localhost:8080/actuator/health | jq .  # gateway
curl -s http://localhost:8082/actuator/health | jq .  # job-monitoring
curl -s http://localhost:8083/actuator/health | jq .  # alerting
curl -s http://localhost:8084/actuator/health | jq .  # notification
curl -s http://localhost:8085/actuator/health | jq .  # job-queue

# Swagger UI
open http://localhost:8082/swagger-ui.html  # job-monitoring
open http://localhost:8083/swagger-ui.html  # alerting
open http://localhost:8084/swagger-ui.html  # notification
open http://localhost:8085/swagger-ui.html  # job-queue

# Prometheus targets
open http://localhost:9090/targets

# Grafana
open http://localhost:3000  # admin / admin
```

Expected health response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 8. Production Considerations

### 8.1 Security Checklist

```
+---+---------------------------------------------------------------+
|   | Item                                                          |
+---+---------------------------------------------------------------+
| 1 | Change JWT_SECRET to a strong 256-bit key                     |
| 2 | Change DB_PASSWORD, REDIS_PASSWORD, CONFIG_PASSWORD            |
| 3 | Enable SMTP_AUTH and SMTP_TLS                                 |
| 4 | Set HEALTH_SHOW_DETAILS=never for public-facing services      |
| 5 | Use TLS termination (HTTPS) at load balancer                  |
| 6 | Restrict actuator endpoints to internal network                |
| 7 | Configure Kafka SASL/SSL authentication                       |
| 8 | Use IAM roles for AWS (no static credentials)                 |
+---+---------------------------------------------------------------+
```

### 8.2 Scaling Guidelines

```
+---------------------------+-----------------------------+------------------+
| Component                 | Scaling Strategy            | Notes            |
+---------------------------+-----------------------------+------------------+
| Services (:8080-:8085)    | Horizontal (N replicas)     | Stateless, LB    |
| job-worker                | Horizontal (N replicas)     | Kafka partitions |
| notification-worker       | Horizontal (N replicas)     | Kafka partitions |
| TimescaleDB               | Vertical first, then HA     | Read replicas    |
| Redis                     | Sentinel or Cluster mode    | HA required      |
| Kafka                     | 3+ brokers, replicas=3     | Partition count  |
+---------------------------+-----------------------------+------------------+
```

### 8.3 Production profile overrides

```bash
# Example production deployment environment
export SPRING_PROFILES_ACTIVE=prod
export DB_POOL_SIZE=30
export DB_POOL_MIN_IDLE=10
export KAFKA_DEFAULT_REPLICAS=3
export KAFKA_LISTENER_CONCURRENCY=6
```

---

## 9. Monitoring Setup

### 9.1 Prometheus

The Prometheus instance scrapes all services every 15 seconds:

```yaml
# deploy/docker/prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'job-monitoring-platform'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
        - 'host.docker.internal:8080'
        - 'host.docker.internal:8081'
        - 'host.docker.internal:8082'
        - 'host.docker.internal:8083'
        - 'host.docker.internal:8084'
        - 'host.docker.internal:8085'
```

### 9.2 Key Metrics

| Metric                                    | Description                    |
|------------------------------------------|--------------------------------|
| `http_server_requests_seconds_count`     | Request count by endpoint      |
| `http_server_requests_seconds_sum`       | Total request duration         |
| `jvm_memory_used_bytes`                  | JVM heap/non-heap usage        |
| `hikaricp_connections_active`            | Active DB connections          |
| `kafka_consumer_records_consumed_total`  | Kafka messages consumed        |
| `spring_kafka_listener_seconds`          | Kafka listener processing time |

### 9.3 Grafana

Access at `http://localhost:3000` (admin/admin).

1. Add Prometheus data source: `http://jobmonitor-prometheus:9090`
2. Import dashboards for Spring Boot, JVM, Kafka, HikariCP

---

## 10. Troubleshooting

### 10.1 Common Issues

| Issue                          | Cause                           | Fix                                    |
|-------------------------------|---------------------------------|----------------------------------------|
| `Connection refused :5432`    | PostgreSQL not started          | `docker compose up -d postgres`        |
| `Kafka broker not available`  | Kafka health check failing      | Wait 30s after startup; check logs     |
| `Flyway migration failed`     | Schema already exists           | `docker compose down -v` + restart     |
| `Redis auth failed`           | Wrong password                  | Check `REDIS_PASSWORD=redis_secret`    |
| `JWT signature invalid`       | Secret mismatch across services | Ensure same `JWT_SECRET` everywhere    |
| `429 Too Many Requests`       | Rate limit exceeded             | Wait 1 minute or increase `RATE_LIMIT_RPM` |
| `S3 upload failed`            | LocalStack not running          | `docker compose up -d localstack`      |

### 10.2 Useful Commands

```bash
# Check Kafka topics
docker exec jobmonitor-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Check Kafka consumer lag
docker exec jobmonitor-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups

# Connect to TimescaleDB
docker exec -it jobmonitor-postgres psql -U jobmonitor -d jobmonitor

# Check hypertable chunks
SELECT * FROM timescaledb_information.chunks
WHERE hypertable_name = 'job_executions';

# Redis check
docker exec jobmonitor-redis redis-cli -a redis_secret INFO keyspace

# View application logs
./gradlew :services:job-monitoring-service:bootRun 2>&1 | jq .
```
