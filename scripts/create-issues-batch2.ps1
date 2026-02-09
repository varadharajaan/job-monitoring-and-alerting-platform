#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════
# GitHub Issues — Batch 2 (Notification, Job Queue, Workers, Gateway, Deploy)
# ═══════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"

# ── Issue 17: Notification Domain ──
gh issue create `
  --title "[NOTIF-017] Notification Service — Domain Model & Template Engine" `
  --label "P0-blocker,phase-4-notification" `
  --body "## Overview
Domain model for the multi-channel notification service. Supports Email, SMS, Slack, Push, and Webhook delivery with template-based rendering.

## Business Context
Send notifications via Email, SMS, Push, Slack. Template management. Delivery tracking and analytics. Rate limiting per channel.
**Revenue**: Pay-per-notification + platform fee | **Competitors**: Twilio, OneSignal, Courier

## Deliverables
- [ ] ``NotificationTemplate`` entity — name, channel, subject, bodyTemplate, variables[], tenantId
- [ ] ``Notification`` entity — templateId, channel, recipient, status (QUEUED/SENDING/SENT/DELIVERED/FAILED/BOUNCED), sentAt, retryCount
- [ ] ``DeliveryReceipt`` entity — notificationId, provider, providerMessageId, deliveredAt, status, errorMessage
- [ ] Template rendering engine (Thymeleaf for HTML email / Mustache for text)
- [ ] Flyway migration ``V3__create_notification_tables.sql``
- [ ] DTOs + MapStruct mappers

## Branch
``feat/017-notification-domain``

## Blocked By
- #1, #5"
Write-Host "Issue 17 created"

# ── Issue 18: Notification Channels ──
gh issue create `
  --title "[NOTIF-018] Notification Service — Multi-Channel Providers (Email, SMS, Slack, Push)" `
  --label "P0-blocker,phase-4-notification" `
  --body "## Overview
Strategy pattern implementation for notification delivery across multiple channels with rate limiting.

## Architecture
```
NotificationEvent → ChannelRouter → EmailChannel    → SendGrid/SES
                                  → SmsChannel      → Twilio
                                  → SlackChannel     → Slack Webhooks
                                  → PushChannel      → Firebase/APNs
                                  → WebhookChannel   → Custom HTTP
```

## Deliverables
- [ ] ``NotificationChannel`` interface (strategy pattern) — ``send(notification)``, ``supports(channel)``
- [ ] ``EmailChannel`` — SendGrid/SES integration with HTML template support
- [ ] ``SmsChannel`` — Twilio integration
- [ ] ``SlackChannel`` — Slack Incoming Webhook integration
- [ ] ``PushChannel`` — Firebase Cloud Messaging (stub for now)
- [ ] ``WebhookChannel`` — generic HTTP POST to user-configured URL
- [ ] ``ChannelRouter`` — routes notification to correct channel implementation
- [ ] Rate limiting per channel (Redis token bucket, limits from ``platform.notification.rate-limit``)
- [ ] Delivery status tracking + ``DeliveryReceipt`` persistence

## Branch
``feat/018-notification-channels``

## Blocked By
- #17"
Write-Host "Issue 18 created"

# ── Issue 19: Notification Pipeline ──
gh issue create `
  --title "[NOTIF-019] Notification Service — Kafka Consumer & Async Delivery Pipeline" `
  --label "P0-blocker,phase-4-notification,kafka" `
  --body "## Overview
Kafka-driven notification delivery pipeline with async processing, retry with dead-letter, and delivery analytics.

## Flow
```
NotificationEvent (Kafka) → NotificationConsumer → TemplateRenderer → RateLimiter → ChannelRouter → Provider
                                                                                                      ↓
                                                                                              DeliveryReceipt → DB
```

## Deliverables
- [ ] Kafka consumer for ``job-monitor.notification-events``
- [ ] Async delivery pipeline (``notificationExecutor`` thread pool)
- [ ] Template variable resolution + rendering
- [ ] Retry with exponential backoff on transient failures
- [ ] Dead-letter after max retries
- [ ] Delivery analytics REST API:
  - ``GET /api/v1/notifications`` — list with pagination
  - ``GET /api/v1/notifications/{id}`` — detail with delivery receipts
  - ``GET /api/v1/notifications/stats`` — sent/delivered/failed/bounced by channel
- [ ] Metrics: notification_sent_total, notification_failed_total, delivery_latency_seconds

## Branch
``feat/019-notification-pipeline``

## Blocked By
- #17, #18, #6"
Write-Host "Issue 19 created"

# ── Issue 20: Job Queue Domain ──
gh issue create `
  --title "[QUEUE-020] Job Queue Service — Domain Model & Priority Queue" `
  --label "P1-high,phase-5-job-queue" `
  --body "## Overview
Multi-tenant background job queue — the 'Sidekiq/Celery for Java'. Distributed job processing with prioritization and retry logic.

## Business Context
**Revenue**: USD 29-199/month | **Competitors**: AWS SQS, Google Cloud Tasks, IronWorker

## Deliverables
- [ ] ``QueuedJob`` entity — type, payload (JSONB), priority (0-10), status, scheduledAt, startedAt, completedAt, tenantId, workerNodeId
- [ ] ``JobType`` registry — registered job handler mapping (type string → handler class)
- [ ] Priority queue backed by Redis sorted sets (score = priority + timestamp for FIFO within priority)
- [ ] Flyway migration ``V4__create_queue_tables.sql``
- [ ] DTOs + MapStruct mappers

## Branch
``feat/020-job-queue-domain``

## Blocked By
- #1, #5"
Write-Host "Issue 20 created"

# ── Issue 21: Job Queue Processing ──
gh issue create `
  --title "[QUEUE-021] Job Queue Service — Distributed Job Processing Engine" `
  --label "P1-high,phase-5-job-queue" `
  --body "## Overview
Core processing engine: polls Redis queue, acquires distributed locks, dispatches to typed handlers, handles timeouts and retries.

## Deliverables
- [ ] ``JobHandler<T>`` interface — ``handle(T payload)``, ``getJobType()``
- [ ] ``JobDispatcher`` — polls Redis sorted set, assigns to worker threads (``jobQueueExecutor``)
- [ ] Redis distributed locking via ``SET NX EX`` (prevent double-processing)
- [ ] Job timeout enforcement (configurable ``platform.job-queue.max-execution-time``)
- [ ] Retry with backoff on failure (max retries from ``platform.job-queue.max-retries``)
- [ ] Progress reporting — ``JobProgressTracker.update(jobId, percent, message)``
- [ ] Emit ``QueueEvent`` to Kafka on state changes
- [ ] Graceful shutdown: complete in-flight jobs before termination

## Branch
``feat/021-job-processing``

## Blocked By
- #20, #6"
Write-Host "Issue 21 created"

# ── Issue 22: Job Queue API ──
gh issue create `
  --title "[QUEUE-022] Job Queue Service — REST API & Monitoring Dashboard" `
  --label "P1-high,phase-5-job-queue" `
  --body "## Overview
REST API for enqueuing, monitoring, and managing background jobs.

## Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | ``/api/v1/queue/jobs`` | Enqueue a new job |
| GET | ``/api/v1/queue/jobs`` | List jobs (paginated, filterable by status/type/priority) |
| GET | ``/api/v1/queue/jobs/{id}`` | Job detail with progress |
| DELETE | ``/api/v1/queue/jobs/{id}`` | Cancel a pending/running job |
| POST | ``/api/v1/queue/jobs/{id}/retry`` | Retry a failed job |
| GET | ``/api/v1/queue/stats`` | Queue stats: depth, processing rate, worker utilization |

## Deliverables
- [ ] All endpoints above
- [ ] WebSocket endpoint for real-time job status updates (optional)
- [ ] Integration tests

## Branch
``feat/022-job-queue-api``

## Blocked By
- #20, #21"
Write-Host "Issue 22 created"

# ── Issue 23: Job Worker ──
gh issue create `
  --title "[WORKER-023] Job Worker — Standalone Job Execution Worker" `
  --label "P1-high,phase-6-workers" `
  --body "## Overview
Standalone Spring Boot application that consumes from the job queue and executes jobs in isolation.

## Deliverables
- [ ] Kafka consumer for ``job-monitor.queue-events``
- [ ] Job execution sandbox (timeout enforcement, resource limits)
- [ ] Heartbeat reporting back to job-queue-service (periodic liveness ping)
- [ ] Graceful shutdown — complete in-flight jobs, deregister from queue
- [ ] Health check endpoint
- [ ] Configurable worker concurrency and poll interval from properties
- [ ] Docker image with resource limits

## Branch
``feat/023-job-worker``

## Blocked By
- #20, #21"
Write-Host "Issue 23 created"

# ── Issue 24: Notification Worker ──
gh issue create `
  --title "[WORKER-024] Notification Worker — High-Volume Notification Delivery" `
  --label "P1-high,phase-6-workers" `
  --body "## Overview
Dedicated worker for high-volume notification delivery, consuming from Kafka with batch processing and parallel channel delivery.

## Deliverables
- [ ] Kafka consumer for bulk notifications from ``job-monitor.notification-events``
- [ ] Batch processing (configurable batch size)
- [ ] Parallel channel delivery (separate thread per channel)
- [ ] Rate limiter integration (Redis token bucket)
- [ ] Delivery receipt persistence
- [ ] Metrics: throughput, latency per channel, failure rate

## Branch
``feat/024-notification-worker``

## Blocked By
- #17, #18"
Write-Host "Issue 24 created"

# ── Issue 25: API Gateway ──
gh issue create `
  --title "[GW-025] Ingestion Gateway — API Gateway with Rate Limiting" `
  --label "P1-high,phase-7-gateway" `
  --body "## Overview
Spring Cloud Gateway as the single entry point for all external API traffic.

## Deliverables
- [ ] ``services/ingestion-gateway/`` module with ``spring-cloud-gateway``
- [ ] Route definitions for all downstream services (job-monitor, alerting, notification, job-queue)
- [ ] Global rate limiting (Redis-backed ``RequestRateLimiter``)
- [ ] Request/response logging filter
- [ ] CORS configuration (from properties)
- [ ] API key validation pass-through to auth-service
- [ ] Circuit breaker per route (Resilience4j)
- [ ] Load balancing for multiple service instances

## Branch
``feat/025-gateway``

## Blocked By
- #1, #7"
Write-Host "Issue 25 created"

# ── Issue 26: Auth Service ──
gh issue create `
  --title "[AUTH-026] Auth Service — API Key Management & RBAC" `
  --label "P1-high,phase-7-gateway,security" `
  --body "## Overview
Dedicated auth service for API key lifecycle management and role-based access control.

## Deliverables
- [ ] API key CRUD: create, rotate, revoke, list
- [ ] API key scoping (per-service permissions: ``job:read``, ``job:write``, ``notification:send``, etc.)
- [ ] Per-key rate limiting
- [ ] Usage tracking (requests per key per day)
- [ ] Key hashing (store only bcrypt hash, not plaintext)
- [ ] REST API with admin endpoints
- [ ] Integration tests

## Branch
``feat/026-auth-service``

## Blocked By
- #1, #7"
Write-Host "Issue 26 created"

# ── Issue 27: Docker Compose ──
gh issue create `
  --title "[DEPLOY-027] Docker Compose for Full Local Development Stack" `
  --label "P1-high,phase-8-deploy" `
  --body "## Overview
Docker Compose configuration to run the entire platform locally with all services and infrastructure.

## Current State
Basic ``docker-compose.yml`` exists with: Postgres+TimescaleDB, Kafka (KRaft), Redis, LocalStack, Prometheus, Grafana.

## Deliverables
- [ ] Move existing docker-compose to ``deploy/docker/``
- [ ] Add Dockerfile per service module (multi-stage: build + runtime)
- [ ] ``docker-compose.yml`` — infrastructure only (postgres, kafka, redis, etc.)
- [ ] ``docker-compose.services.yml`` — all application services (override file)
- [ ] Health check configuration for all services
- [ ] Volume mounts for local development
- [ ] ``.env.example`` with all environment variables documented
- [ ] Network isolation (``platform-net``)

## Branch
``feat/027-docker``

## Blocked By
- #1"
Write-Host "Issue 27 created"

# ── Issue 28: Kubernetes ──
gh issue create `
  --title "[DEPLOY-028] Kubernetes Manifests & Helm Chart" `
  --label "P2-medium,phase-8-deploy" `
  --body "## Overview
Production-grade Kubernetes deployment manifests for all services.

## Deliverables
- [ ] Deployment + Service + Ingress per service
- [ ] ConfigMap for application config
- [ ] Secret management (External Secrets Operator placeholder)
- [ ] HPA (Horizontal Pod Autoscaler) per service
- [ ] Resource requests/limits
- [ ] Liveness + readiness probes
- [ ] Helm chart (optional, future)
- [ ] Namespace isolation

## Branch
``feat/028-k8s``

## Blocked By
- #27"
Write-Host "Issue 28 created"

# ── Issue 29: Integration Tests ──
gh issue create `
  --title "[TEST-029] End-to-End Integration Tests" `
  --label "P1-high,phase-9-integration" `
  --body "## Overview
Full end-to-end integration tests covering the complete platform flow.

## Test Scenarios
1. **Job lifecycle**: Register job → Record execution → SLA violation → Alert triggered → Notification sent
2. **Job queue lifecycle**: Enqueue → Worker picks up → Processing → Completion/Failure → Retry
3. **Notification delivery**: Template creation → Send request → Channel delivery → Delivery receipt
4. **Alert management**: Create rule → Trigger event → Alert created → Acknowledge → Resolve

## Deliverables
- [ ] Testcontainers setup: PostgreSQL + TimescaleDB, Kafka, Redis
- [ ] Test fixtures + data builders
- [ ] Integration test per scenario above
- [ ] CI pipeline configuration (GitHub Actions)

## Branch
``feat/029-integration-tests``

## Blocked By
- #11, #15, #19, #22"
Write-Host "Issue 29 created"

# ── Issue 30: Observability ──
gh issue create `
  --title "[OPS-030] Observability — Metrics, Dashboards & Distributed Tracing" `
  --label "P2-medium,phase-9-integration" `
  --body "## Overview
Full observability stack with custom metrics, Grafana dashboards, and distributed tracing.

## Deliverables
- [ ] Custom Micrometer metrics per service:
  - ``job.execution.duration`` (histogram), ``job.execution.total`` (counter by status)
  - ``alert.triggered.total`` (counter by severity), ``alert.evaluation.duration``
  - ``notification.sent.total`` (counter by channel), ``notification.delivery.latency``
  - ``queue.depth`` (gauge), ``queue.processing.rate``, ``queue.worker.utilization``
- [ ] Grafana dashboard JSON files in ``deploy/grafana/``
- [ ] Distributed tracing via Micrometer Tracing + Zipkin/Jaeger
- [ ] Prometheus alerting rules (``deploy/prometheus/alerts.yml``)
- [ ] Health dashboard showing all services status

## Branch
``feat/030-observability``

## Blocked By
- #1"
Write-Host "Issue 30 created"

# ── Issue 31: Log Ingestion (Future) ──
gh issue create `
  --title "[FUTURE-031] Log Ingestion Service — Real-Time Log Aggregation & Search (Stage 2)" `
  --label "P3-low,future-scope" `
  --body "## Overview — FUTURE SCOPE (Stage 2)
Collect logs from multiple servers, full-text search with Elasticsearch, pattern alerting, custom dashboards.

## Business Context
**Revenue**: USD 50-500/month based on volume | **Competitors**: Datadog, Splunk, LogDNA

## Planned Features
- [ ] Multi-server log collection via Kafka
- [ ] Elasticsearch integration for full-text search
- [ ] Logstash pipeline configuration
- [ ] Log pattern alerting (regex-based + ML anomaly detection)
- [ ] Custom dashboards (Kibana or custom UI)
- [ ] Log retention policies
- [ ] REST API for log search and filtering

## Tech Stack Addition
Elasticsearch, Logstash, Kibana (ELK)

## Status
Stub only — module directory created, no implementation yet."
Write-Host "Issue 31 created"

# ── Issue 32: DB Performance (Future) ──
gh issue create `
  --title "[FUTURE-032] Database Query Performance Analyzer (Stage 3)" `
  --label "P3-low,future-scope" `
  --body "## Overview — FUTURE SCOPE (Stage 3)
Monitor slow queries in production, suggest indexes, explain plan visualization, query rewrite suggestions.

## Business Context
**Revenue**: USD 99-499/month | **Competitors**: Datadog, New Relic, SolarWinds

## Planned Features
- [ ] Slow query monitoring (PostgreSQL ``pg_stat_statements``, MySQL ``performance_schema``)
- [ ] Index suggestion engine
- [ ] ``EXPLAIN ANALYZE`` plan visualization (tree/graph rendering)
- [ ] Query rewrite suggestions (basic rule engine + ML)
- [ ] Historical query performance tracking
- [ ] Cost estimation per query
- [ ] REST API for query analysis

## Tech Stack Addition
PostgreSQL/MySQL drivers, ML libraries (optional)

## Status
Stub only — module directory created, no implementation yet."
Write-Host "Issue 32 created"

# ── Issue 33: Architecture Docs ──
gh issue create `
  --title "[DOCS-033] Architecture Documentation & ADRs" `
  --label "P1-high,architecture" `
  --body "## Overview
Comprehensive architecture documentation in ``docs/`` for anyone reviewing the repository.

## Deliverables
- [ ] ``docs/architecture/OVERVIEW.md`` — system architecture, service interactions, data flow
- [ ] ``docs/architecture/DATA_MODEL.md`` — entity relationships, database design
- [ ] ``docs/architecture/EVENT_CATALOG.md`` — all Kafka events, schemas, producers, consumers
- [ ] ``docs/architecture/API_CONTRACTS.md`` — REST API summary across all services
- [ ] ``docs/architecture/DEPLOYMENT.md`` — deployment topology, scaling strategy
- [ ] ``docs/adr/001-monorepo-gradle-multi-module.md``
- [ ] ``docs/adr/002-kafka-event-bus.md``
- [ ] ``docs/adr/003-timescaledb-for-time-series.md``
- [ ] ``docs/adr/004-redis-caching-and-rate-limiting.md``
- [ ] ``docs/adr/005-multi-tenancy-strategy.md``
- [ ] System architecture diagram (Mermaid or PlantUML)

## Branch
``feat/033-docs``

## Blocked By
- #1"
Write-Host "Issue 33 created"

Write-Host "ALL_ISSUES_CREATED"