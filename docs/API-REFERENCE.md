# API Reference

> **Job Monitoring & Alerting Platform** — REST API Documentation  
> Base URL: `http://localhost:{port}` | Format: JSON | Auth: JWT Bearer Token

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Common Response Format](#2-common-response-format)
3. [Error Response Format](#3-error-response-format)
4. [Job Monitoring API](#4-job-monitoring-api-8082)
5. [Alerting API](#5-alerting-api-8083)
6. [Notification API](#6-notification-api-8084)
7. [Job Queue API](#7-job-queue-api-8085)
8. [Ingestion Gateway](#8-ingestion-gateway-8080)
9. [Auth Service](#9-auth-service-8081)
10. [Actuator Endpoints](#10-actuator-endpoints)
11. [Kafka Topics](#11-kafka-topics)
12. [Error Codes Reference](#12-error-codes-reference)

---

## 1. Authentication

All API requests (except public paths) require a JWT Bearer token:

```
Authorization: Bearer <access_token>
```

**Public paths (no auth required):**
- `/api-docs/**`
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/actuator/health/**`
- `/actuator/info`

**Token lifecycle:**

```
POST /api/v1/auth/login
    |
    +---> Access Token  (1 hour TTL)
    +---> Refresh Token (24 hour TTL)

POST /api/v1/auth/refresh
    |
    +---> New Access Token (1 hour TTL)
```

**JWT claims:**

| Claim      | Description                      |
|-----------|----------------------------------|
| `sub`     | User ID (UUID)                   |
| `tenantId`| Tenant ID for multi-tenant scope |
| `roles`   | Array of role strings            |
| `iss`     | `job-monitor-platform`           |
| `exp`     | Expiration timestamp             |

---

## 2. Common Response Format

All successful responses use `ApiResponse<T>`:

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation completed successfully",
  "timestamp": "2026-02-10T12:00:00Z"
}
```

Paginated responses use `PageResponse<T>`:

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "first": true,
    "last": false
  },
  "timestamp": "2026-02-10T12:00:00Z"
}
```

---

## 3. Error Response Format

All errors follow `ApiError`:

```json
{
  "timestamp": "2026-02-10T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Job not found with id: 550e8400-e29b-41d4-a716-446655440000",
  "path": "/api/v1/jobs/550e8400-e29b-41d4-a716-446655440000",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fieldErrors": null
}
```

Validation error (with field details):

```json
{
  "timestamp": "2026-02-10T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_FAILED",
  "message": "Validation failed",
  "path": "/api/v1/jobs",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fieldErrors": [
    {
      "field": "name",
      "message": "must not be blank",
      "rejectedValue": null
    },
    {
      "field": "cronExpression",
      "message": "invalid cron expression",
      "rejectedValue": "not-a-cron"
    }
  ]
}
```

---

## 4. Job Monitoring API (:8082)

**Base path:** `/api/v1`  
**SpringDoc:** `http://localhost:8082/swagger-ui.html`

### 4.1 Jobs

```
+--------+------------------------------+----------------------------------+
| Method | Path                         | Description                      |
+--------+------------------------------+----------------------------------+
| POST   | /api/v1/jobs                 | Register a new monitored job     |
| GET    | /api/v1/jobs                 | List jobs (paginated, filtered)  |
| GET    | /api/v1/jobs/{id}            | Get job by ID                    |
| PUT    | /api/v1/jobs/{id}            | Update job configuration         |
| DELETE | /api/v1/jobs/{id}            | Deactivate a job                 |
| GET    | /api/v1/jobs/search          | Search jobs by tags/name/status  |
+--------+------------------------------+----------------------------------+
```

**POST /api/v1/jobs** — Register job

```json
// Request
{
  "name": "nightly-etl-pipeline",
  "description": "Extracts data from source DB, transforms, loads to warehouse",
  "cronExpression": "0 0 2 * * *",
  "scheduleType": "CRON",
  "slaSeconds": 3600,
  "gracePeriodSeconds": 300,
  "expectedRuntimeSeconds": 1800,
  "timeoutSeconds": 7200,
  "maxRetries": 3,
  "tags": ["etl", "warehouse", "nightly"],
  "metadata": { "team": "data-engineering", "tier": "critical" }
}

// Response: 201 Created
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "...",
    "name": "nightly-etl-pipeline",
    "status": "ACTIVE",
    "createdAt": "2026-02-10T12:00:00Z"
  }
}
```

### 4.2 Job Executions

```
+--------+---------------------------------------------+--------------------------+
| Method | Path                                        | Description              |
+--------+---------------------------------------------+--------------------------+
| POST   | /api/v1/jobs/{jobId}/executions              | Record an execution      |
| GET    | /api/v1/jobs/{jobId}/executions              | List executions (paged)  |
| GET    | /api/v1/jobs/{jobId}/executions/{execId}     | Get execution detail     |
| POST   | /api/v1/jobs/{jobId}/executions/{execId}/retry| Retry failed execution  |
| GET    | /api/v1/jobs/{jobId}/executions/stats        | Hourly aggregated stats  |
+--------+---------------------------------------------+--------------------------+
```

**POST /api/v1/jobs/{jobId}/executions** — Record execution

```json
// Request
{
  "status": "SUCCESS",
  "startedAt": "2026-02-10T02:00:00Z",
  "completedAt": "2026-02-10T02:25:00Z",
  "durationMs": 1500000,
  "exitCode": 0,
  "output": "Processed 1,234,567 records",
  "metadata": { "recordsProcessed": 1234567 }
}

// Response: 201 Created
```

**GET /api/v1/jobs/{jobId}/executions/stats** — Hourly stats

```json
// Response (from continuous aggregate)
{
  "success": true,
  "data": [
    {
      "bucket": "2026-02-10T00:00:00Z",
      "totalRuns": 12,
      "successCount": 10,
      "failureCount": 2,
      "avgDurationMs": 45000,
      "maxDurationMs": 120000,
      "p95DurationMs": 95000
    }
  ]
}
```

### 4.3 Query Parameters

| Parameter  | Type   | Description                  | Example              |
|-----------|--------|------------------------------|----------------------|
| `page`    | int    | Page number (0-based)        | `0`                  |
| `size`    | int    | Page size (max 100)          | `20`                 |
| `sort`    | string | Sort field,direction         | `createdAt,desc`     |
| `status`  | string | Filter by status             | `ACTIVE`             |
| `tags`    | string | Filter by tag (repeatable)   | `tags=etl&tags=nightly` |
| `from`    | ISO    | Start of time range          | `2026-02-01T00:00:00Z` |
| `to`      | ISO    | End of time range            | `2026-02-10T00:00:00Z` |

---

## 5. Alerting API (:8083)

**Base path:** `/api/v1`  
**SpringDoc:** `http://localhost:8083/swagger-ui.html`

### 5.1 Alert Rules

```
+--------+------------------------------+----------------------------------+
| Method | Path                         | Description                      |
+--------+------------------------------+----------------------------------+
| POST   | /api/v1/alert-rules          | Create alert rule                |
| GET    | /api/v1/alert-rules          | List rules (paginated)           |
| GET    | /api/v1/alert-rules/{id}     | Get rule by ID                   |
| PUT    | /api/v1/alert-rules/{id}     | Update rule                      |
| DELETE | /api/v1/alert-rules/{id}     | Delete rule                      |
| PATCH  | /api/v1/alert-rules/{id}/toggle | Enable/disable rule           |
+--------+------------------------------+----------------------------------+
```

**POST /api/v1/alert-rules** — Create rule

```json
// Request
{
  "name": "ETL Failure Alert",
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "ruleType": "FAILURE_THRESHOLD",
  "conditionJson": {
    "threshold": 3,
    "windowMinutes": 60,
    "operator": "GREATER_THAN"
  },
  "severity": "HIGH",
  "notificationChannels": ["EMAIL", "SLACK"],
  "cooldownSeconds": 600
}

// Response: 201 Created
```

### 5.2 Alert History

```
+--------+------------------------------+----------------------------------+
| Method | Path                         | Description                      |
+--------+------------------------------+----------------------------------+
| GET    | /api/v1/alerts               | List triggered alerts (paged)    |
| GET    | /api/v1/alerts/{id}          | Get alert detail                 |
| POST   | /api/v1/alerts/{id}/ack      | Acknowledge alert                |
| POST   | /api/v1/alerts/{id}/resolve  | Resolve alert                    |
+--------+------------------------------+----------------------------------+
```

---

## 6. Notification API (:8084)

**Base path:** `/api/v1`  
**SpringDoc:** `http://localhost:8084/swagger-ui.html`

### 6.1 Templates

```
+--------+-------------------------------------+------------------------------+
| Method | Path                                | Description                  |
+--------+-------------------------------------+------------------------------+
| POST   | /api/v1/notification-templates      | Create template              |
| GET    | /api/v1/notification-templates      | List templates               |
| GET    | /api/v1/notification-templates/{id} | Get template                 |
| PUT    | /api/v1/notification-templates/{id} | Update template              |
| DELETE | /api/v1/notification-templates/{id} | Delete template              |
+--------+-------------------------------------+------------------------------+
```

**POST /api/v1/notification-templates** — Create template

```json
// Request
{
  "name": "job-failure-alert",
  "channel": "EMAIL",
  "subject": "[ALERT] Job {{jobName}} failed",
  "body": "Job {{jobName}} failed at {{failedAt}}.\nError: {{errorMessage}}\nAttempt: {{attempt}}/{{maxRetries}}",
  "variables": ["jobName", "failedAt", "errorMessage", "attempt", "maxRetries"]
}
```

### 6.2 Notifications

```
+--------+-------------------------------------+------------------------------+
| Method | Path                                | Description                  |
+--------+-------------------------------------+------------------------------+
| POST   | /api/v1/notifications/send          | Send a notification          |
| GET    | /api/v1/notifications               | List notifications (paged)   |
| GET    | /api/v1/notifications/{id}          | Get notification status      |
| POST   | /api/v1/notifications/{id}/cancel   | Cancel pending notification  |
+--------+-------------------------------------+------------------------------+
```

**POST /api/v1/notifications/send** — Send notification

```json
// Request
{
  "channel": "SLACK",
  "recipient": "#ops-alerts",
  "subject": "Job Failure Alert",
  "body": "nightly-etl-pipeline failed after 3 retries",
  "priority": 1,
  "metadata": { "jobId": "550e...", "severity": "HIGH" }
}

// Response: 202 Accepted
{
  "success": true,
  "data": {
    "id": "...",
    "status": "PENDING",
    "channel": "SLACK"
  }
}
```

---

## 7. Job Queue API (:8085)

**Base path:** `/api/v1`  
**SpringDoc:** `http://localhost:8085/swagger-ui.html`

### 7.1 Queue Management

```
+--------+-----------------------------------+----------------------------------+
| Method | Path                              | Description                      |
+--------+-----------------------------------+----------------------------------+
| POST   | /api/v1/queue/jobs                | Submit a background job          |
| GET    | /api/v1/queue/jobs                | List queued jobs (paged)         |
| GET    | /api/v1/queue/jobs/{id}           | Get job status                   |
| DELETE | /api/v1/queue/jobs/{id}           | Cancel a pending job             |
| POST   | /api/v1/queue/jobs/{id}/retry     | Retry a failed job               |
| GET    | /api/v1/queue/jobs/{id}/result    | Get job result                   |
| GET    | /api/v1/queue/dead-letter         | List dead-letter items           |
| POST   | /api/v1/queue/dead-letter/{id}/replay | Replay from dead letter     |
+--------+-----------------------------------+----------------------------------+
```

**POST /api/v1/queue/jobs** — Submit job

```json
// Request
{
  "queueName": "reports",
  "jobType": "GENERATE_MONTHLY_REPORT",
  "payload": {
    "reportType": "executive-summary",
    "month": "2026-01",
    "format": "PDF",
    "recipients": ["alice@example.com"]
  },
  "priority": 2,
  "maxRetries": 5,
  "timeoutSeconds": 1800
}

// Response: 202 Accepted
{
  "success": true,
  "data": {
    "id": "...",
    "status": "PENDING",
    "queueName": "reports",
    "priority": 2
  }
}
```

---

## 8. Ingestion Gateway (:8080)

The gateway is the single entry point for all client requests. It handles:

```
+--------------------------------------------------------------------------+
|                        Gateway Responsibilities                          |
+--------------------------------------------------------------------------+
|                                                                          |
|  1. Rate Limiting (Bucket4j)                                             |
|     - Default: 60 requests/minute per client                             |
|     - Burst: 10 extra requests                                           |
|     - Response when exceeded: 429 + RATE_LIMIT_EXCEEDED                  |
|                                                                          |
|  2. JWT Validation                                                       |
|     - Extracts Bearer token from Authorization header                    |
|     - Validates signature, expiry, claims                                |
|     - Propagates tenantId/userId to downstream services                  |
|                                                                          |
|  3. Request Routing                                                      |
|     - /api/v1/jobs/**        -> job-monitoring-service:8082              |
|     - /api/v1/alert-rules/** -> alerting-service:8083                    |
|     - /api/v1/alerts/**      -> alerting-service:8083                    |
|     - /api/v1/notifications/**  -> notification-service:8084             |
|     - /api/v1/queue/**       -> job-queue-service:8085                   |
|     - /api/v1/auth/**        -> auth-service:8081                        |
|                                                                          |
|  4. Request/Response Logging                                             |
|     - MdcLoggingFilter: traceId, tenantId, userId, method, uri           |
|                                                                          |
+--------------------------------------------------------------------------+
```

---

## 9. Auth Service (:8081)

```
+--------+------------------------------+----------------------------------+
| Method | Path                         | Description                      |
+--------+------------------------------+----------------------------------+
| POST   | /api/v1/auth/login           | Authenticate (returns JWT pair)  |
| POST   | /api/v1/auth/refresh         | Refresh access token             |
| POST   | /api/v1/auth/logout          | Invalidate refresh token         |
| GET    | /api/v1/auth/me              | Get current user profile         |
+--------+------------------------------+----------------------------------+
```

**POST /api/v1/auth/login**

```json
// Request
{
  "email": "admin@example.com",
  "password": "********"
}

// Response: 200 OK
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

---

## 10. Actuator Endpoints

All services expose health and metrics endpoints:

```
+------------------------------+---------+--------------------------------------+
| Endpoint                     | Auth    | Description                          |
+------------------------------+---------+--------------------------------------+
| /actuator/health             | Public  | Liveness check (UP/DOWN)             |
| /actuator/health/liveness    | Public  | K8s liveness probe                   |
| /actuator/health/readiness   | Public  | K8s readiness probe                  |
| /actuator/info               | Public  | Build info, git commit               |
| /actuator/prometheus         | Auth    | Prometheus metrics scrape            |
| /actuator/env                | Admin   | Environment properties (config-srv)  |
+------------------------------+---------+--------------------------------------+
```

Prometheus scrapes all service metrics every 15 seconds at `/actuator/prometheus`.

---

## 11. Kafka Topics

```
+-------------------------------------+-----------+----------+---------------------------+
| Topic                               | Partitions| Replicas | Producers                 |
+-------------------------------------+-----------+----------+---------------------------+
| job-monitor.job-events              | 6         | 1 (dev)  | job-monitoring-service    |
| job-monitor.alert-events            | 6         | 1 (dev)  | alerting-service          |
| job-monitor.notification-events     | 6         | 1 (dev)  | notification-service      |
| job-monitor.queue-events            | 6         | 1 (dev)  | job-queue-service         |
| job-monitor.dead-letter             | 6         | 1 (dev)  | all (error handler)       |
+-------------------------------------+-----------+----------+---------------------------+

Consumer Groups:
+------------------------------+--------------------------------------+
| Group ID                     | Service                              |
+------------------------------+--------------------------------------+
| job-monitoring-group         | job-monitoring-service                |
| alerting-group               | alerting-service                     |
| notification-group           | notification-service                 |
| job-queue-group              | job-queue-service                    |
| job-worker-group             | job-worker                           |
| notification-worker-group    | notification-worker                  |
+------------------------------+--------------------------------------+
```

**Event types (Jackson polymorphic via @JsonTypeInfo):**

| Event Class        | Actions                                                              |
|-------------------|----------------------------------------------------------------------|
| `JobEvent`        | REGISTERED, STARTED, COMPLETED, FAILED, SLA_VIOLATED, RETRYING, HEARTBEAT_MISSED |
| `AlertEvent`      | TRIGGERED, ACKNOWLEDGED, RESOLVED, SUPPRESSED, ESCALATED            |
| `NotificationEvent`| REQUESTED, SENT, DELIVERED, FAILED, CANCELLED                      |
| `QueueEvent`      | SUBMITTED, LOCKED, STARTED, COMPLETED, FAILED, RETRY, DEAD_LETTER  |

---

## 12. Error Codes Reference

Complete list of error codes returned in `ApiError.errorCode`:

```
+---------------------------+------+-------------------------------------------+
| Error Code                | HTTP | When Returned                             |
+---------------------------+------+-------------------------------------------+
| BUSINESS_ERROR            | 400  | Generic business rule violation            |
| RESOURCE_NOT_FOUND        | 404  | Entity not found by ID                     |
| DUPLICATE_RESOURCE        | 409  | Unique constraint violation                |
| RATE_LIMIT_EXCEEDED       | 429  | Gateway rate limit or channel limit        |
| UNAUTHORIZED              | 401  | Missing or invalid JWT                     |
| FORBIDDEN                 | 403  | Insufficient role/permissions              |
| AUTHENTICATION_FAILED     | 401  | Bad credentials on login                   |
| ACCESS_DENIED             | 403  | Spring Security access denied              |
| SERVICE_UNAVAILABLE       | 503  | Kafka/Redis/S3 down                        |
| REQUEST_TIMEOUT           | 408  | Downstream call or lock acquisition timeout|
| OPTIMISTIC_LOCK_CONFLICT  | 409  | Concurrent update detected (@Version)      |
| VALIDATION_FAILED         | 400  | @Valid constraint failures                 |
| BINDING_FAILED            | 400  | Request binding errors                     |
| CONSTRAINT_VIOLATION       | 400  | Jakarta Validation constraint              |
| MISSING_PARAMETER         | 400  | Required query/path param missing          |
| MALFORMED_REQUEST         | 400  | Unreadable JSON body                       |
| TYPE_MISMATCH             | 400  | Wrong parameter type                       |
| METHOD_NOT_ALLOWED        | 405  | Wrong HTTP method                          |
| UNSUPPORTED_MEDIA_TYPE    | 415  | Wrong Content-Type header                  |
| ENDPOINT_NOT_FOUND        | 404  | No handler for URL                         |
| DATA_INTEGRITY_VIOLATION  | 409  | DB constraint violation (FK, unique)       |
| INTERNAL_ERROR            | 500  | Unhandled server exception                 |
+---------------------------+------+-------------------------------------------+
```

**Headers for correlation:**

| Header         | Description                          |
|---------------|--------------------------------------|
| `X-Trace-Id`  | Correlation ID (propagated via MDC)  |
| `X-Tenant-Id` | Tenant context for multi-tenancy     |
