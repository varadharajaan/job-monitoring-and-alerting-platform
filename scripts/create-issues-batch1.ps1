#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
# GitHub Issues Creation Script
# Run: pwsh ./scripts/create-issues.ps1
# ═══════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"

# ── Issue 4: Centralized Exception Handling ──
gh issue create `
  --title "[PLATFORM-004] Centralized Exception Handling & Error Response Format" `
  --label "P0-blocker,phase-1-platform" `
  --body "## Overview
Global ``@ControllerAdvice`` with structured ``ApiError`` JSON responses and correlation IDs across all services.

## Problem
``BusinessException`` exists but no ``@ControllerAdvice`` to catch it. No standardized error response format.

## Deliverables
- [ ] ``GlobalExceptionHandler`` with ``@RestControllerAdvice``
- [ ] ``ApiError`` DTO — timestamp, status, errorCode, message, traceId, path, fieldErrors
- [ ] Handle: ``BusinessException``, ``MethodArgumentNotValidException``, ``ConstraintViolationException``, ``HttpMessageNotReadableException``, generic ``Exception``
- [ ] ``ResourceNotFoundException``, ``DuplicateResourceException``, ``RateLimitExceededException``
- [ ] MDC traceId/requestId propagation in error responses
- [ ] Unit tests for all exception handlers

## Branch
``feat/004-exception-handling``

## Blocked By
- #1"
Write-Host "Issue 4 created"

# ── Issue 5: Base Models & DTOs ──
gh issue create `
  --title "[PLATFORM-005] Base Models, DTOs & MapStruct Mapper Infrastructure" `
  --label "P0-blocker,phase-1-platform" `
  --body "## Overview
Shared domain models and DTOs used across all services in ``platform/common``.

## Deliverables
- [ ] ``BaseEntity`` — UUID id, createdAt, updatedAt, version (JPA auditing), tenantId
- [ ] ``ApiResponse<T>`` — standardized response envelope (success, data, message, timestamp)
- [ ] ``PageResponse<T>`` — standardized pagination (content, page, size, totalElements, totalPages, first, last)
- [ ] ``SortDirection`` enum
- [ ] ``BaseMapper`` interface for MapStruct conventions

## Design
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;
    @Version private Long version;
    private String tenantId;
}
```

## Branch
``feat/005-base-models``

## Blocked By
- #1"
Write-Host "Issue 5 created"

# ── Issue 6: Kafka Event Infrastructure ──
gh issue create `
  --title "[PLATFORM-006] Kafka Event Infrastructure & Shared Event Schema" `
  --label "P0-blocker,phase-1-platform,kafka" `
  --body "## Overview
Shared Kafka event classes with Jackson polymorphic deserialization, event publisher abstraction, and dead-letter handling.

## Deliverables
- [ ] ``PlatformEvent`` base class — eventId, eventType, timestamp, source, tenantId, correlationId
- [ ] ``JobEvent`` — REGISTERED, STARTED, COMPLETED, FAILED, SLA_VIOLATED, RETRYING, HEARTBEAT_MISSED
- [ ] ``AlertEvent`` — TRIGGERED, ACKNOWLEDGED, RESOLVED, ESCALATED + severity levels
- [ ] ``NotificationEvent`` — REQUESTED, QUEUED, SENT, DELIVERED, FAILED, BOUNCED + channels (EMAIL, SMS, SLACK, PUSH)
- [ ] ``QueueEvent`` — ENQUEUED, PROCESSING, COMPLETED, FAILED, RETRYING, CANCELLED, TIMED_OUT
- [ ] ``EventPublisher`` interface + Kafka implementation
- [ ] Jackson ``@JsonTypeInfo`` + ``@JsonSubTypes`` for polymorphic deser
- [ ] Dead-letter event wrapper with original event + failure reason
- [ ] Unit tests for serialization/deserialization

## Event Flow Diagram
```
JobMonitorService → JobEvent → Kafka → AlertingService → AlertEvent → Kafka → NotificationService → NotificationEvent
                                                                                      ↓
                                                                              NotificationWorker
```

## Branch
``feat/006-kafka-events``

## Blocked By
- #1"
Write-Host "Issue 6 created"

# ── Issue 7: Auth & Multi-Tenancy ──
gh issue create `
  --title "[PLATFORM-007] Authentication, API Key Management & Multi-Tenancy" `
  --label "P1-high,phase-1-platform,security" `
  --body "## Overview
API key authentication, tenant context propagation, and security filters shared across all services.

## Deliverables
- [ ] ``TenantContext`` — ThreadLocal-based tenant ID propagation
- [ ] ``ApiKeyAuthenticationFilter`` — Spring Security filter extracting API key from ``X-API-Key`` header
- [ ] ``ApiKey`` entity and repository (key hash, tenantId, scopes, rateLimit, expiresAt)
- [ ] ``@TenantScoped`` annotation for automatic JPA query filtering
- [ ] ``RequestIdFilter`` — MDC population with requestId, traceId, tenantId
- [ ] Kafka header propagation for tenantId and correlationId
- [ ] Unit + integration tests

## Security Flow
```
Request → ApiKeyFilter → TenantContext.set(tenantId) → Controller → Service → Repository (@TenantScoped)
                                                                        ↓
                                                                  Kafka (tenantId in header)
```

## Branch
``feat/007-auth-tenancy``

## Blocked By
- #1, #4"
Write-Host "Issue 7 created"

# ── Issue 8: JSON Logging & S3 Upload ──
gh issue create `
  --title "[PLATFORM-008] JSON Structured Logging (JSONL) & S3 Log Upload for Athena" `
  --label "P1-high,phase-1-platform" `
  --body "## Overview
JSONL structured logging with rolling file appenders, async S3 upload of rotated logs, and Athena-queryable partitioned storage.

## Current State
``logback-spring.xml`` already configured with:
- Console JSON via ``LogstashEncoder`` (dev)
- ``application.jsonl`` rolling 50MB/30 days/2GB cap
- ``audit.jsonl`` separate 90 days/5GB cap
- MDC keys: traceId, spanId, tenantId, requestId

## Remaining Deliverables
- [ ] ``LogUploadService`` — scheduled S3 upload of rolled JSONL files (cron from properties)
- [ ] S3 path partitioning: ``s3://{bucket}/logs/{service}/{yyyy}/{MM}/{dd}/{filename}.jsonl``
- [ ] Athena CREATE TABLE DDL for log querying
- [ ] Cleanup of successfully uploaded local logs
- [ ] Integration test with LocalStack S3
- [ ] Metrics: logs uploaded, upload failures, upload latency

## Branch
``feat/008-log-upload``

## Blocked By
- #1, #2"
Write-Host "Issue 8 created"

# ── Issue 9: Event & API Schemas ──
gh issue create `
  --title "[PLATFORM-009] Event & API Schema Definitions (platform/schema)" `
  --label "P1-high,phase-1-platform" `
  --body "## Overview
Centralized schema definitions for all Kafka events and REST API contracts, enabling contract-first development and cross-service validation.

## Deliverables
- [ ] JSON Schema files for: ``JobEvent``, ``AlertEvent``, ``NotificationEvent``, ``QueueEvent``
- [ ] OpenAPI schema fragments for shared DTOs (``ApiResponse``, ``PageResponse``, ``ApiError``)
- [ ] ``SchemaValidator`` utility for validating events against schemas
- [ ] Schema versioning strategy (semantic versioning for event schemas)
- [ ] Gradle task to generate POJOs from JSON Schema (optional)
- [ ] README documenting schema evolution rules

## Branch
``feat/009-schemas``

## Blocked By
- #1, #6"
Write-Host "Issue 9 created"

# ── Issue 10: Job Monitor Domain ──
gh issue create `
  --title "[JOB-010] Job Monitoring Service — Domain Model & Persistence" `
  --label "P0-blocker,phase-2-job-monitor" `
  --body "## Overview
Core entities and persistence layer for the scheduled job & cron monitoring service.

## Business Context
Monitor all scheduled jobs in one place. Alert when jobs fail or run late. Execution history & logs. Retry failed jobs.
**Revenue**: USD 29-149/month | **Competitors**: Datadog, PagerDuty, Cronitor

## Deliverables
- [ ] ``Job`` entity — name, description, cronExpression, expectedDurationMs, slaThresholdMs, status, tenantId, tags, enabled
- [ ] ``JobExecution`` entity — jobId, startTime, endTime, status (RUNNING/SUCCESS/FAILED/TIMED_OUT), exitCode, output, error, triggeredBy
- [ ] ``JobSlaViolation`` entity — jobId, executionId, violationType (LATE_START/LONG_RUNNING/MISSED), detectedAt, resolved
- [ ] Flyway migration ``V1__create_job_tables.sql``
- [ ] TimescaleDB hypertable on ``job_execution`` (time-partitioned on ``start_time``)
- [ ] Spring Data JPA repositories with custom queries (find overdue, find by status, execution history)
- [ ] DTOs + MapStruct mappers
- [ ] Unit tests for mappers

## Data Model
```sql
CREATE TABLE job (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(100),
    expected_duration_ms BIGINT,
    sla_threshold_ms BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    version BIGINT DEFAULT 0
);
CREATE INDEX idx_job_tenant ON job(tenant_id);

CREATE TABLE job_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID REFERENCES job(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    exit_code INTEGER,
    output TEXT,
    error TEXT,
    triggered_by VARCHAR(50) DEFAULT 'SCHEDULER'
);
SELECT create_hypertable('job_execution', 'start_time');
```

## Branch
``feat/010-job-monitor-domain``

## Blocked By
- #1, #5"
Write-Host "Issue 10 created"

# ── Issue 11: Job Monitor API ──
gh issue create `
  --title "[JOB-011] Job Monitoring Service — REST API (CRUD + Search)" `
  --label "P0-blocker,phase-2-job-monitor" `
  --body "## Overview
Full REST API for the job monitoring service with pagination, filtering, and OpenAPI docs.

## Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | ``/api/v1/jobs`` | Register a monitored job |
| GET | ``/api/v1/jobs`` | List jobs (paginated, filterable by status/tenant/tags) |
| GET | ``/api/v1/jobs/{id}`` | Job detail with last N executions |
| PUT | ``/api/v1/jobs/{id}`` | Update job configuration |
| DELETE | ``/api/v1/jobs/{id}`` | Soft delete job |
| GET | ``/api/v1/jobs/{id}/executions`` | Execution history (paginated, time-range filter) |
| POST | ``/api/v1/jobs/{id}/executions`` | Record a job execution (external trigger) |
| POST | ``/api/v1/jobs/{id}/retry`` | Retry last failed execution |

## Deliverables
- [ ] ``JobController`` with all endpoints above
- [ ] ``JobService`` business logic layer
- [ ] Request validation (``@Valid``, custom validators for cron expressions)
- [ ] OpenAPI annotations (``@Operation``, ``@ApiResponse``, ``@Tag``)
- [ ] Integration tests with Testcontainers (Postgres + Kafka)

## Branch
``feat/011-job-monitor-api``

## Blocked By
- #10, #4"
Write-Host "Issue 11 created"

# ── Issue 12: Heartbeat & SLA ──
gh issue create `
  --title "[JOB-012] Job Monitoring — Heartbeat Monitoring & SLA Evaluation Engine" `
  --label "P0-blocker,phase-2-job-monitor" `
  --body "## Overview
Detect when scheduled jobs miss their expected execution window or run longer than their SLA.

## Deliverables
- [ ] ``POST /api/v1/heartbeat/{jobId}`` — job heartbeat ping endpoint
- [ ] ``HeartbeatMonitorService`` — scheduled task detecting missing heartbeats (interval from properties)
- [ ] ``SlaEvaluationService`` — cron-based SLA check (cron from ``platform.job-monitor.sla-evaluation-cron``)
- [ ] Emit ``JobEvent(SLA_VIOLATED)`` and ``JobEvent(HEARTBEAT_MISSED)`` to Kafka
- [ ] Late start detection (expected vs actual execution time based on cron)
- [ ] Long-running detection (execution duration > sla_threshold_ms)
- [ ] Configurable grace periods per job
- [ ] Redis-backed heartbeat timestamp tracking
- [ ] Unit + integration tests

## Branch
``feat/012-heartbeat-sla``

## Blocked By
- #10, #6"
Write-Host "Issue 12 created"

# ── Issue 13: Retry Engine ──
gh issue create `
  --title "[JOB-013] Job Monitoring — Retry Engine with Exponential Backoff" `
  --label "P1-high,phase-2-job-monitor" `
  --body "## Overview
Smart retry mechanism for failed jobs with exponential backoff, configurable max attempts, and Resilience4j integration.

## Deliverables
- [ ] ``RetryService`` with exponential backoff (multiplier from ``platform.job-monitor.retry-backoff-multiplier``)
- [ ] Max retry attempts from ``platform.job-monitor.max-retry-attempts``
- [ ] Retry state tracking in DB (attempt number, next retry time, last error)
- [ ] Resilience4j ``@Retry`` + ``@CircuitBreaker`` integration
- [ ] Kafka event emission on retry start (``RETRYING``) and outcome (``COMPLETED``/``FAILED``)
- [ ] Exponential backoff formula: ``delay = baseDelay * (multiplier ^ attemptNumber)``
- [ ] Dead-letter after max retries exhausted
- [ ] Unit tests

## Branch
``feat/013-retry-engine``

## Blocked By
- #10, #11"
Write-Host "Issue 13 created"

# ── Issue 14: Alerting Domain ──
gh issue create `
  --title "[ALERT-014] Alerting Service — Domain Model & Alert Rules" `
  --label "P0-blocker,phase-3-alerting" `
  --body "## Overview
Domain model for the alerting service — rules engine that watches job events and triggers alerts.

## Deliverables
- [ ] ``AlertRule`` entity — name, condition, threshold, severity, channels[], cooldownMinutes, tenantId, enabled
- [ ] ``Alert`` entity — ruleId, triggeredAt, status (OPEN/ACKNOWLEDGED/RESOLVED), acknowledgedBy, resolvedAt, jobId
- [ ] ``AlertCondition`` value object — metricName, operator (GT/LT/EQ/NEQ), threshold, durationMinutes
- [ ] Flyway migration ``V2__create_alert_tables.sql``
- [ ] Repositories + DTOs + MapStruct mappers
- [ ] Predefined rule templates (job-failed, sla-violated, heartbeat-missed)

## Branch
``feat/014-alerting-domain``

## Blocked By
- #1, #5"
Write-Host "Issue 14 created"

# ── Issue 15: Alert Rule Engine ──
gh issue create `
  --title "[ALERT-015] Alerting Service — Rule Evaluation & Event Processing Engine" `
  --label "P0-blocker,phase-3-alerting,kafka" `
  --body "## Overview
Kafka consumer that evaluates incoming job events against alert rules and triggers notifications.

## Flow
```
JobEvent (Kafka) → AlertingService.evaluate() → match rules → deduplicate → AlertEvent (Kafka) → NotificationService
```

## Deliverables
- [ ] Kafka consumer for ``job-monitor.job-events`` topic
- [ ] Rule matching engine (evaluate ``AlertCondition`` against ``JobEvent``)
- [ ] Alert deduplication — suppress duplicates within cooldown window (Redis-backed)
- [ ] Alert escalation — severity upgrade after N triggers within time window
- [ ] Emit ``AlertEvent(TRIGGERED)`` to ``job-monitor.alert-events`` topic
- [ ] Emit ``NotificationEvent(REQUESTED)`` to ``job-monitor.notification-events`` topic
- [ ] Metrics: alerts triggered, alerts deduplicated, evaluation latency

## Branch
``feat/015-alert-engine``

## Blocked By
- #14, #6"
Write-Host "Issue 15 created"

# ── Issue 16: Alerting API ──
gh issue create `
  --title "[ALERT-016] Alerting Service — REST API" `
  --label "P1-high,phase-3-alerting" `
  --body "## Overview
REST API for managing alert rules and viewing alert history.

## Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | ``/api/v1/alerts/rules`` | Create alert rule |
| GET | ``/api/v1/alerts/rules`` | List rules (paginated) |
| PUT | ``/api/v1/alerts/rules/{id}`` | Update rule |
| DELETE | ``/api/v1/alerts/rules/{id}`` | Delete rule |
| GET | ``/api/v1/alerts`` | List active alerts (filterable by severity/status) |
| POST | ``/api/v1/alerts/{id}/acknowledge`` | Acknowledge alert |
| POST | ``/api/v1/alerts/{id}/resolve`` | Resolve alert |
| GET | ``/api/v1/alerts/stats`` | Alert statistics (counts by severity/status) |

## Branch
``feat/016-alerting-api``

## Blocked By
- #14, #4"
Write-Host "Issue 16 created"

Write-Host "BATCH_1_DONE"