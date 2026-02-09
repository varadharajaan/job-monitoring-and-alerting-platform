# Job Monitoring & Alerting Platform — Progress Tracker

> **Last updated**: 2026-02-09
> **Architecture**: Gradle Multi-Module Monorepo
> **Java**: 17 | **Spring Boot**: 3.2.5 | **Build**: Gradle

---

## Phase 0: Foundation & Infrastructure Setup

### ISSUE-001: Create multi-module Gradle skeleton structure
- **Status**: 🔲 NOT STARTED
- **Priority**: P0 — BLOCKER
- **Description**: Restructure from single Maven module to Gradle multi-module monorepo
- **Deliverables**:
  - [ ] Root `settings.gradle` with all subproject includes
  - [ ] Root `build.gradle` with shared config (Java 17, deps management, plugins)
  - [ ] `build/` — shared Gradle configs (code quality, jacoco, docker)
  - [ ] `platform/common/` — shared library module (auth, events, errors, logging)
  - [ ] `platform/schema/` — event & API schema module (Avro/JSON schemas)
  - [ ] `services/auth-service/`
  - [ ] `services/ingestion-gateway/`
  - [ ] `services/alerting-service/`
  - [ ] `services/notification-service/`
  - [ ] `services/job-monitoring-service/`
  - [ ] `services/job-queue-service/`
  - [ ] `services/log-ingestion-service/` (stage 2 — stub only)
  - [ ] `services/db-performance-service/` (stage 3 — stub only)
  - [ ] `workers/job-worker/`
  - [ ] `workers/notification-worker/`
  - [ ] `ui/web-console/` (stub)
  - [ ] `deploy/docker/` — docker-compose files
  - [ ] `deploy/k8s/` — Kubernetes manifests (stub)
  - [ ] `docs/` — architecture docs, ADRs
- **Branch**: `feat/001-gradle-multi-module`

### ISSUE-002: Centralize ALL configuration — ZERO hardcoded values
- **Status**: 🔲 NOT STARTED
- **Priority**: P0 — BLOCKER
- **Description**: Every config value MUST come from `application.yml` → `PlatformProperties`. No magic numbers.
- **Deliverables**:
  - [ ] Add `AsyncProperties` to `PlatformProperties` (pool sizes, queue capacities, thread prefixes, timeouts)
  - [ ] Add `KafkaTopicProperties` with partition/replica counts per topic
  - [ ] Add `KafkaConcurrencyProperties` (listener concurrency, error backoff interval, max retries)
  - [ ] Add `CacheProperties` with named cache TTLs (jobs, notifications, templates, rate-limits, default)
  - [ ] Refactor `AsyncConfig.java` — read ALL values from properties
  - [ ] Refactor `KafkaConfig.java` — read partition/replica/concurrency from properties
  - [ ] Refactor `RedisConfig.java` — read cache TTLs from properties
  - [ ] Update `application.yml` with all new config sections
  - [ ] Update `application-dev.yml` and `application-prod.yml` with environment-specific overrides
- **Branch**: `feat/002-centralize-config`

### ISSUE-003: Spring Cloud Config Server setup
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Description**: Add Spring Cloud Config Server for centralized config management across services
- **Deliverables**:
  - [ ] `services/config-server/` module with `spring-cloud-config-server`
  - [ ] Git-backed config repository support
  - [ ] All service modules configured as config clients
  - [ ] Encrypted secrets support
  - [ ] `bootstrap.yml` / `spring.config.import` in each service
- **Branch**: `feat/003-config-server`

### ISSUE-004: Centralized exception handling & error response format
- **Status**: 🔲 NOT STARTED — PARTIAL (`BusinessException` exists)
- **Priority**: P0
- **Description**: Global `@ControllerAdvice`, structured error JSON, correlation IDs
- **Deliverables**:
  - [ ] `GlobalExceptionHandler` with `@ControllerAdvice`
  - [ ] `ApiError` DTO (timestamp, status, errorCode, message, traceId, path, fieldErrors)
  - [ ] Handle: `BusinessException`, `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, generic `Exception`
  - [ ] MDC traceId/requestId propagation in error responses
  - [ ] Unit tests for all exception handlers
- **Branch**: `feat/004-exception-handling`

---

## Phase 1: Core Platform (platform/common)

### ISSUE-005: platform/common — Base models, DTOs, and MapStruct mappers
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Description**: Shared domain models used across all services
- **Deliverables**:
  - [ ] `BaseEntity` — `id`, `createdAt`, `updatedAt`, `version` (JPA auditing)
  - [ ] `PageResponse<T>` — standardized pagination DTO
  - [ ] `ApiResponse<T>` — standardized API response envelope
  - [ ] `SortDirection` enum
  - [ ] `BaseMapper` interface for MapStruct conventions
- **Branch**: `feat/005-base-models`

### ISSUE-006: platform/common — Kafka event infrastructure
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Description**: Shared event classes, serializers, and Kafka producer/consumer abstractions
- **Deliverables**:
  - [ ] `PlatformEvent` base class (eventId, eventType, timestamp, source, tenantId, payload)
  - [ ] `JobEvent`, `NotificationEvent`, `QueueEvent`, `AlertEvent` subclasses
  - [ ] `EventPublisher` interface + Kafka implementation
  - [ ] `EventSerializer` / `EventDeserializer` (JSON with schema validation)
  - [ ] Dead-letter event wrapper
- **Branch**: `feat/006-kafka-events`

### ISSUE-007: platform/common — Auth & multi-tenancy foundation
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Description**: API key auth, tenant context, security filters
- **Deliverables**:
  - [ ] `TenantContext` (ThreadLocal-based tenant ID propagation)
  - [ ] `ApiKeyAuthenticationFilter` (Spring Security filter)
  - [ ] `ApiKey` entity and repository
  - [ ] `@TenantScoped` annotation for auto-filtering queries
  - [ ] Request/correlation ID filter (MDC population)
- **Branch**: `feat/007-auth-tenancy`

### ISSUE-008: platform/common — JSON structured logging & S3 log upload
- **Status**: 🔲 NOT STARTED — PARTIAL (`logback-spring.xml` exists)
- **Priority**: P1
- **Description**: JSONL logging, rolling files, async S3 upload for Athena queries
- **Deliverables**:
  - [ ] Verify `logback-spring.xml` JSONL output (already done ✅)
  - [ ] `LogUploadService` — scheduled S3 upload of rolled JSONL files
  - [ ] S3 path partitioning: `s3://bucket/logs/{service}/{yyyy}/{MM}/{dd}/{filename}.jsonl`
  - [ ] Athena table DDL for log querying
  - [ ] Cleanup of uploaded local logs
  - [ ] Integration test with LocalStack S3
- **Branch**: `feat/008-log-upload`

### ISSUE-009: platform/schema — Event & API schema definitions
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Description**: Shared schema definitions for events and API contracts
- **Deliverables**:
  - [ ] JSON Schema files for all event types
  - [ ] OpenAPI schema fragments for shared DTOs
  - [ ] Schema validation utility
  - [ ] Schema versioning strategy doc
- **Branch**: `feat/009-schemas`

---

## Phase 2: Module 1 — Scheduled Job & Cron Monitoring Service

### ISSUE-010: job-monitoring-service — Domain model & persistence
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Description**: Core entities for job monitoring
- **Deliverables**:
  - [ ] `Job` entity (name, cronExpression, expectedDuration, sla, status, tenantId, tags)
  - [ ] `JobExecution` entity (jobId, startTime, endTime, status, exitCode, output, error)
  - [ ] `JobSlaViolation` entity (jobId, executionId, violationType, detectedAt)
  - [ ] Flyway migrations (`V1__create_job_tables.sql`)
  - [ ] TimescaleDB hypertable for `job_execution` (time-series partitioning)
  - [ ] Spring Data JPA repositories with custom queries
  - [ ] DTOs + MapStruct mappers
- **Branch**: `feat/010-job-monitor-domain`

### ISSUE-011: job-monitoring-service — REST API (CRUD + search)
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] `POST /api/v1/jobs` — register a job
  - [ ] `GET /api/v1/jobs` — list with pagination, filtering, sorting
  - [ ] `GET /api/v1/jobs/{id}` — job detail with last N executions
  - [ ] `PUT /api/v1/jobs/{id}` — update job config
  - [ ] `DELETE /api/v1/jobs/{id}` — soft delete
  - [ ] `GET /api/v1/jobs/{id}/executions` — execution history
  - [ ] `POST /api/v1/jobs/{id}/retry` — retry last failed execution
  - [ ] Input validation, OpenAPI docs
  - [ ] Integration tests with Testcontainers
- **Branch**: `feat/011-job-monitor-api`

### ISSUE-012: job-monitoring-service — Heartbeat & SLA evaluation
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] `POST /api/v1/heartbeat/{jobId}` — job heartbeat endpoint
  - [ ] `HeartbeatMonitorService` — detect missing heartbeats (Spring Scheduler)
  - [ ] `SlaEvaluationService` — cron-based SLA check (configurable cron from properties)
  - [ ] Emit `JobSlaViolationEvent` to Kafka on violation
  - [ ] Late job detection (expected vs actual execution time)
  - [ ] Configurable grace periods per job
- **Branch**: `feat/012-heartbeat-sla`

### ISSUE-013: job-monitoring-service — Retry engine
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] `RetryService` with exponential backoff (configurable via properties)
  - [ ] Max retry attempts from `PlatformProperties.jobMonitor.maxRetryAttempts`
  - [ ] Retry state tracking in DB
  - [ ] Resilience4j retry + circuit breaker integration
  - [ ] Kafka event emission on retry success/failure
- **Branch**: `feat/013-retry-engine`

---

## Phase 3: Module 2 — Alerting Service

### ISSUE-014: alerting-service — Domain model & alert rules
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] `AlertRule` entity (name, condition, threshold, severity, channels, tenantId)
  - [ ] `Alert` entity (ruleId, triggeredAt, status, acknowledgedBy, resolvedAt)
  - [ ] `AlertCondition` value object (metricName, operator, threshold, duration)
  - [ ] Flyway migrations
  - [ ] Repositories + DTOs + mappers
- **Branch**: `feat/014-alerting-domain`

### ISSUE-015: alerting-service — Rule evaluation engine
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] Kafka consumer for `JobEvent`, `JobSlaViolationEvent`
  - [ ] Rule matching engine (evaluate conditions against events)
  - [ ] Alert deduplication (suppress duplicate alerts within window)
  - [ ] Alert escalation (severity upgrade after N triggers)
  - [ ] Emit `NotificationEvent` to Kafka when alert triggers
- **Branch**: `feat/015-alert-engine`

### ISSUE-016: alerting-service — REST API
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] CRUD for alert rules
  - [ ] List active/historical alerts
  - [ ] Acknowledge/resolve alerts
  - [ ] Alert statistics endpoint
- **Branch**: `feat/016-alerting-api`

---

## Phase 4: Module 3 — Multi-Channel Notification Service

### ISSUE-017: notification-service — Domain model & templates
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] `NotificationTemplate` entity (name, channel, subject, body, variables)
  - [ ] `Notification` entity (templateId, channel, recipient, status, sentAt, retryCount)
  - [ ] `DeliveryReceipt` entity (notificationId, provider, providerMessageId, deliveredAt, status)
  - [ ] Template rendering engine (Thymeleaf or Mustache)
  - [ ] Flyway migrations
- **Branch**: `feat/017-notification-domain`

### ISSUE-018: notification-service — Channel providers (Email, SMS, Slack, Push)
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] `NotificationChannel` interface (strategy pattern)
  - [ ] `EmailChannel` — SendGrid/SES integration
  - [ ] `SmsChannel` — Twilio integration
  - [ ] `SlackChannel` — Slack webhook integration
  - [ ] `PushChannel` — Firebase/APNs integration (stub)
  - [ ] Channel selection based on alert rule config
  - [ ] Rate limiting per channel (from `PlatformProperties.notification.rateLimit`)
  - [ ] Delivery status tracking
- **Branch**: `feat/018-notification-channels`

### ISSUE-019: notification-service — Kafka consumer & delivery pipeline
- **Status**: 🔲 NOT STARTED
- **Priority**: P0
- **Deliverables**:
  - [ ] Kafka consumer for `NotificationEvent`
  - [ ] Async delivery pipeline (uses `notificationExecutor` thread pool)
  - [ ] Retry with dead-letter on permanent failure
  - [ ] Delivery analytics (sent/delivered/failed/bounced counts)
  - [ ] REST API for delivery status and analytics
- **Branch**: `feat/019-notification-pipeline`

---

## Phase 5: Module 4 — Multi-Tenant Background Job Queue

### ISSUE-020: job-queue-service — Domain model & job definition
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] `QueuedJob` entity (type, payload, priority, status, scheduledAt, startedAt, completedAt, tenantId)
  - [ ] `JobType` registry (registered job handlers)
  - [ ] Priority queue implementation (Redis sorted set)
  - [ ] Flyway migrations
- **Branch**: `feat/020-job-queue-domain`

### ISSUE-021: job-queue-service — Distributed job processing
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] `JobHandler` interface (strategy pattern for job types)
  - [ ] `JobDispatcher` — polls queue, assigns to workers
  - [ ] Worker thread pool (uses `jobQueueExecutor`)
  - [ ] Redis-based distributed locking (prevent double processing)
  - [ ] Job timeout handling
  - [ ] Progress reporting API
- **Branch**: `feat/021-job-processing`

### ISSUE-022: job-queue-service — REST API & monitoring
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] `POST /api/v1/queue/jobs` — enqueue job
  - [ ] `GET /api/v1/queue/jobs/{id}` — job status
  - [ ] `DELETE /api/v1/queue/jobs/{id}` — cancel job
  - [ ] `GET /api/v1/queue/stats` — queue depth, processing rate, worker utilization
  - [ ] WebSocket endpoint for real-time job status updates
- **Branch**: `feat/022-job-queue-api`

---

## Phase 6: Workers

### ISSUE-023: workers/job-worker — Standalone job execution worker
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] Kafka consumer for `QueueEvent`
  - [ ] Job execution sandbox (timeout, resource limits)
  - [ ] Heartbeat reporting back to job-queue-service
  - [ ] Graceful shutdown with in-flight job completion
- **Branch**: `feat/023-job-worker`

### ISSUE-024: workers/notification-worker — Dedicated notification delivery worker
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] Kafka consumer for high-volume notification delivery
  - [ ] Batch processing for bulk notifications
  - [ ] Rate limiter integration
  - [ ] Parallel channel delivery
- **Branch**: `feat/024-notification-worker`

---

## Phase 7: API Gateway & Auth

### ISSUE-025: ingestion-gateway — API Gateway with rate limiting
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] Spring Cloud Gateway module
  - [ ] Route definitions for all downstream services
  - [ ] Global rate limiting (Redis-backed)
  - [ ] Request/response logging
  - [ ] CORS configuration
  - [ ] API key validation filter
- **Branch**: `feat/025-gateway`

### ISSUE-026: auth-service — Authentication & API key management
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] API key CRUD (create, rotate, revoke)
  - [ ] API key scoping (per-service permissions)
  - [ ] Rate limit per API key
  - [ ] Usage tracking
- **Branch**: `feat/026-auth-service`

---

## Phase 8: Deployment & DevOps

### ISSUE-027: deploy/docker — Docker Compose for all services
- **Status**: 🔲 NOT STARTED — PARTIAL (base docker-compose exists)
- **Priority**: P1
- **Deliverables**:
  - [ ] Dockerfile per service module
  - [ ] `docker-compose.yml` with all services + infra
  - [ ] Health check configuration
  - [ ] Volume mounts for local dev
  - [ ] Network isolation between services
- **Branch**: `feat/027-docker`

### ISSUE-028: deploy/k8s — Kubernetes manifests
- **Status**: 🔲 NOT STARTED
- **Priority**: P2
- **Deliverables**:
  - [ ] Deployment + Service + Ingress per service
  - [ ] ConfigMap + Secret management
  - [ ] HPA (Horizontal Pod Autoscaler) configs
  - [ ] Helm chart (optional)
- **Branch**: `feat/028-k8s`

---

## Phase 9: Integration & Testing

### ISSUE-029: End-to-end integration tests
- **Status**: 🔲 NOT STARTED
- **Priority**: P1
- **Deliverables**:
  - [ ] Testcontainers setup for full stack (Postgres, Kafka, Redis)
  - [ ] Job registration → execution → SLA violation → alert → notification flow
  - [ ] Job queue → worker processing → completion flow
  - [ ] API integration tests per service
- **Branch**: `feat/029-integration-tests`

### ISSUE-030: Observability — Metrics, dashboards, tracing
- **Status**: 🔲 NOT STARTED — PARTIAL (Prometheus/Grafana in docker-compose)
- **Priority**: P2
- **Deliverables**:
  - [ ] Custom Micrometer metrics per service
  - [ ] Grafana dashboard JSON definitions
  - [ ] Distributed tracing (Micrometer Tracing + Zipkin/Jaeger)
  - [ ] Alert rules in Prometheus/Alertmanager
- **Branch**: `feat/030-observability`

---

## Future Stages (Not in Scope Yet)

### ISSUE-031: log-ingestion-service (Stage 2)
- **Status**: ⏸️ FUTURE
- Multi-server log collection, Elasticsearch full-text search, pattern alerting, custom dashboards

### ISSUE-032: db-performance-service (Stage 3)
- **Status**: ⏸️ FUTURE
- Slow query monitoring, index suggestions, explain plan visualization, query rewrite suggestions

---

## Summary

| Phase | Issues | Status |
|-------|--------|--------|
| 0: Foundation | 001-004 | 🔲 Not Started |
| 1: Core Platform | 005-009 | 🔲 Not Started |
| 2: Job Monitoring | 010-013 | 🔲 Not Started |
| 3: Alerting | 014-016 | 🔲 Not Started |
| 4: Notifications | 017-019 | 🔲 Not Started |
| 5: Job Queue | 020-022 | 🔲 Not Started |
| 6: Workers | 023-024 | 🔲 Not Started |
| 7: Gateway & Auth | 025-026 | 🔲 Not Started |
| 8: Deployment | 027-028 | 🔲 Not Started |
| 9: Integration | 029-030 | 🔲 Not Started |
| Future | 031-032 | ⏸️ Future |

**Critical Path**: 001 → 002 → 004 → 005 → 006 → 010 → 011 → 012 → 014 → 015 → 017 → 018 → 019
