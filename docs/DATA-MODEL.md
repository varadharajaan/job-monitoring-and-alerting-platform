# Data Model Reference

> **Job Monitoring & Alerting Platform** — Database Schema Documentation  
> Database: PostgreSQL 16 + TimescaleDB Extension  
> Migration tool: Flyway | Migrations: V001 through V006

---

## Table of Contents

1. [Entity Relationship Diagram](#1-entity-relationship-diagram)
2. [Table Reference](#2-table-reference)
3. [TimescaleDB Hypertables](#3-timescaledb-hypertables)
4. [Continuous Aggregates](#4-continuous-aggregates)
5. [Retention Policies](#5-retention-policies)
6. [Index Strategy](#6-index-strategy)
7. [Migration Inventory](#7-migration-inventory)

---

## 1. Entity Relationship Diagram

```
+-------------------+
|     tenants       |
+-------------------+
| PK id        UUID |
|    name   VARCHAR |
|    slug   VARCHAR |
|    plan   VARCHAR |
|    status VARCHAR |
|    metadata JSONB |
|    created_at  TZ |
|    updated_at  TZ |
+--------+----------+
         |
         | 1:N
         |
+--------v----------+          +---------------------+
|       jobs        |          |    alert_rules      |
+-------------------+          +---------------------+
| PK id        UUID |<---+    | PK id          UUID |
| FK tenant_id UUID |    |    | FK tenant_id   UUID |----> tenants
|    name   VARCHAR |    |    | FK job_id      UUID |----> jobs (nullable)
|    cron   VARCHAR |    |    |    name      VARCHAR |
|    schedule_type  |    |    |    rule_type VARCHAR |
|    sla_seconds INT|    |    |    condition   JSONB |
|    grace_period   |    |    |    severity  VARCHAR |
|    timeout_seconds|    |    |    channels    JSONB |
|    max_retries INT|    |    |    cooldown_sec  INT |
|    tags      JSONB|    |    |    enabled   BOOLEAN |
|    metadata  JSONB|    |    |    version    BIGINT |
|    status VARCHAR |    |    |    created_at     TZ |
|    version BIGINT |    |    |    updated_at     TZ |
|    created_at  TZ |    |    +---------+-----------+
|    updated_at  TZ |    |              |
+--------+----------+    |              | 1:N
         |               |              |
         | 1:N           |    +---------v-----------+
         |               |    |   alert_history *   |
+--------v----------+    |    +---------------------+
| job_executions *  |    |    | PK id          UUID |
+-------------------+    |    | FK alert_rule  UUID |
| PK id        UUID |    |    | FK tenant_id   UUID |----> tenants
| PK started_at  TZ |    |    | FK job_id      UUID |----> jobs (nullable)
| FK job_id    UUID |    |    |    severity  VARCHAR |
| FK tenant_id UUID |    |    |    status    VARCHAR |
|    status VARCHAR |    |    |    message      TEXT |
|    completed_at TZ|    |    |    context     JSONB |
|    duration_ms    |    |    |    acknowledged_by   |
|    exit_code  INT |    |    |    acknowledged_at TZ|
|    output    TEXT |    |    |    resolved_at     TZ|
|    error_msg TEXT |    |    | PK triggered_at   TZ|
|    attempt_no INT |    |    +---------------------+
|    metadata JSONB |    |     (* = hypertable)
|    created_at  TZ |    |
+-------------------+    |
 (* = hypertable)        |
                         |
+-------------------+    |    +---------------------+
| notif_templates   |    |    |   notifications *   |
+-------------------+    |    +---------------------+
| PK id        UUID |    |    | PK id          UUID |
| FK tenant_id UUID |    |    | FK tenant_id   UUID |----> tenants
|    name   VARCHAR |    |    | FK template_id UUID |----> notif_templates
|    channel VARCHAR |    |    |    channel   VARCHAR |
|    subject VARCHAR |    |    |    recipient VARCHAR |
|    body      TEXT |    |    |    subject   VARCHAR |
|    variables JSONB|    |    |    body         TEXT |
|    version BIGINT |    |    |    status    VARCHAR |
|    created_at  TZ |    |    |    priority      INT |
|    updated_at  TZ |    |    |    retry_count   INT |
+-------------------+    |    |    max_retries   INT |
                         |    |    error_msg    TEXT |
+-------------------+    |    |    metadata    JSONB |
|    job_queue      |    |    |    scheduled_at   TZ |
+-------------------+    |    |    sent_at        TZ |
| PK id        UUID |    |    |    delivered_at   TZ |
| FK tenant_id UUID +----+    | PK created_at     TZ |
|    queue_name     |         +---------------------+
|    job_type       |          (* = hypertable)
|    payload  JSONB |
|    priority   INT |         +---------------------+
|    status VARCHAR |         | job_queue_dead_letter|
|    max_retries INT|         +---------------------+
|    retry_count INT|         | PK id          UUID |
|    next_retry  TZ |         |    original_id UUID |
|    locked_by      |         | FK tenant_id   UUID |
|    locked_at   TZ |         |    queue_name       |
|    lock_expires TZ|         |    job_type  VARCHAR |
|    started_at  TZ |         |    payload     JSONB |
|    completed_at TZ|         |    error_msg    TEXT |
|    error_msg TEXT |         |    retry_count   INT |
|    result   JSONB |         |    metadata    JSONB |
|    timeout_sec INT|         |    failed_at      TZ |
|    metadata JSONB |         +---------------------+
|    version BIGINT |
|    created_at  TZ |
|    updated_at  TZ |
+-------------------+
```

---

## 2. Table Reference

### 2.1 `tenants` (V001)

Multi-tenant isolation root. Every entity in the system belongs to a tenant.

| Column       | Type          | Constraints                | Default       |
|-------------|---------------|----------------------------|---------------|
| `id`        | UUID          | PRIMARY KEY                | uuid_generate_v4() |
| `name`      | VARCHAR(255)  | NOT NULL, UNIQUE           |               |
| `slug`      | VARCHAR(100)  | NOT NULL, UNIQUE           |               |
| `plan`      | VARCHAR(50)   | NOT NULL                   | `'FREE'`      |
| `status`    | VARCHAR(20)   | NOT NULL                   | `'ACTIVE'`    |
| `metadata`  | JSONB         |                            | `'{}'`        |
| `created_at`| TIMESTAMPTZ   | NOT NULL                   | `now()`       |
| `updated_at`| TIMESTAMPTZ   | NOT NULL                   | `now()`       |

**Indexes:** `idx_tenants_slug (slug)`, `idx_tenants_status (status)`  
**Triggers:** `set_tenants_updated_at` (auto-updates `updated_at`)  
**Plans:** `FREE`, `PRO`, `ENTERPRISE`  
**Statuses:** `ACTIVE`, `SUSPENDED`, `ARCHIVED`

---

### 2.2 `jobs` (V002)

Registered scheduled jobs that the platform monitors.

| Column                   | Type          | Constraints                          | Default    |
|-------------------------|---------------|--------------------------------------|------------|
| `id`                    | UUID          | PRIMARY KEY                          | uuid_generate_v4() |
| `tenant_id`             | UUID          | NOT NULL, FK -> tenants              |            |
| `name`                  | VARCHAR(255)  | NOT NULL                             |            |
| `description`           | TEXT          |                                      |            |
| `cron_expression`       | VARCHAR(100)  |                                      |            |
| `schedule_type`         | VARCHAR(50)   | NOT NULL                             | `'CRON'`   |
| `sla_seconds`           | INT           |                                      |            |
| `grace_period_seconds`  | INT           |                                      | `300`      |
| `expected_runtime_seconds` | INT        |                                      |            |
| `timeout_seconds`       | INT           |                                      | `3600`     |
| `max_retries`           | INT           |                                      | `3`        |
| `tags`                  | JSONB         |                                      | `'[]'`     |
| `metadata`              | JSONB         |                                      | `'{}'`     |
| `status`                | VARCHAR(20)   | NOT NULL                             | `'ACTIVE'` |
| `version`               | BIGINT        | NOT NULL                             | `0`        |
| `created_at`            | TIMESTAMPTZ   | NOT NULL                             | `now()`    |
| `updated_at`            | TIMESTAMPTZ   | NOT NULL                             | `now()`    |

**Unique constraint:** `(tenant_id, name)`  
**Indexes:** `idx_jobs_tenant`, `idx_jobs_status`, `idx_jobs_tags (GIN)`  
**Triggers:** `set_jobs_updated_at`

---

### 2.3 `job_executions` (V003) — Hypertable

Individual execution records. Partitioned by `started_at` for time-series queries.

| Column          | Type          | Constraints                | Default         |
|----------------|---------------|----------------------------|-----------------|
| `id`           | UUID          | COMPOSITE PK (id, started_at) | uuid_generate_v4() |
| `job_id`       | UUID          | NOT NULL, FK -> jobs       |                 |
| `tenant_id`    | UUID          | NOT NULL, FK -> tenants    |                 |
| `status`       | VARCHAR(20)   | NOT NULL                   | `'PENDING'`     |
| `started_at`   | TIMESTAMPTZ   | COMPOSITE PK, partition key| `now()`         |
| `completed_at` | TIMESTAMPTZ   |                            |                 |
| `duration_ms`  | BIGINT        |                            |                 |
| `exit_code`    | INT           |                            |                 |
| `output`       | TEXT          |                            |                 |
| `error_message`| TEXT          |                            |                 |
| `attempt_number`| INT          | NOT NULL                   | `1`             |
| `metadata`     | JSONB         |                            | `'{}'`          |
| `created_at`   | TIMESTAMPTZ   | NOT NULL                   | `now()`         |

**Hypertable config:** chunk interval = 7 days  
**Indexes:** `idx_exec_job (job_id, started_at DESC)`, `idx_exec_tenant`, `idx_exec_status`  
**Retention:** 90 days auto-drop  
**Statuses:** `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `TIMEOUT`, `RETRYING`

---

### 2.4 `alert_rules` (V004)

Configurable alert rules that evaluate against job executions.

| Column                 | Type          | Constraints                | Default        |
|-----------------------|---------------|----------------------------|----------------|
| `id`                  | UUID          | PRIMARY KEY                | uuid_generate_v4() |
| `tenant_id`           | UUID          | NOT NULL, FK -> tenants    |                |
| `job_id`              | UUID          | FK -> jobs (nullable)      |                |
| `name`                | VARCHAR(255)  | NOT NULL                   |                |
| `description`         | TEXT          |                            |                |
| `rule_type`           | VARCHAR(50)   | NOT NULL                   |                |
| `condition_json`      | JSONB         | NOT NULL                   |                |
| `severity`            | VARCHAR(20)   | NOT NULL                   | `'MEDIUM'`     |
| `notification_channels`| JSONB        | NOT NULL                   | `'["EMAIL"]'`  |
| `cooldown_seconds`    | INT           |                            | `300`          |
| `enabled`             | BOOLEAN       | NOT NULL                   | `true`         |
| `version`             | BIGINT        | NOT NULL                   | `0`            |
| `created_at`          | TIMESTAMPTZ   | NOT NULL                   | `now()`        |
| `updated_at`          | TIMESTAMPTZ   | NOT NULL                   | `now()`        |

**Indexes:** `idx_alert_rules_tenant`, `idx_alert_rules_job`, `idx_alert_rules_enabled`  
**Triggers:** `set_alert_rules_updated_at`  
**Severities:** `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`  
**Rule types:** `FAILURE_THRESHOLD`, `SLA_VIOLATION`, `HEARTBEAT_MISS`, `DURATION_ANOMALY`

---

### 2.5 `alert_history` (V004) — Hypertable

Immutable audit log of triggered alerts.

| Column            | Type          | Constraints                      | Default       |
|------------------|---------------|----------------------------------|---------------|
| `id`             | UUID          | COMPOSITE PK (id, triggered_at)  | uuid_generate_v4() |
| `alert_rule_id`  | UUID          | NOT NULL, FK -> alert_rules      |               |
| `tenant_id`      | UUID          | NOT NULL, FK -> tenants          |               |
| `job_id`         | UUID          | FK -> jobs (nullable)            |               |
| `severity`       | VARCHAR(20)   | NOT NULL                         |               |
| `status`         | VARCHAR(20)   | NOT NULL                         | `'TRIGGERED'` |
| `message`        | TEXT          |                                  |               |
| `context_json`   | JSONB         |                                  | `'{}'`        |
| `acknowledged_by`| VARCHAR(255)  |                                  |               |
| `acknowledged_at`| TIMESTAMPTZ   |                                  |               |
| `resolved_at`    | TIMESTAMPTZ   |                                  |               |
| `triggered_at`   | TIMESTAMPTZ   | COMPOSITE PK, partition key      | `now()`       |

**Hypertable config:** chunk interval = 7 days  
**Retention:** 180 days auto-drop  
**Statuses:** `TRIGGERED`, `ACKNOWLEDGED`, `RESOLVED`, `SUPPRESSED`

---

### 2.6 `notification_templates` (V005)

Reusable notification templates per channel.

| Column       | Type          | Constraints                           | Default |
|-------------|---------------|---------------------------------------|---------|
| `id`        | UUID          | PRIMARY KEY                           | uuid_generate_v4() |
| `tenant_id` | UUID          | NOT NULL, FK -> tenants               |         |
| `name`      | VARCHAR(255)  | NOT NULL                              |         |
| `channel`   | VARCHAR(20)   | NOT NULL                              |         |
| `subject`   | VARCHAR(500)  |                                       |         |
| `body`      | TEXT          | NOT NULL                              |         |
| `variables` | JSONB         |                                       | `'[]'`  |
| `version`   | BIGINT        | NOT NULL                              | `0`     |
| `created_at`| TIMESTAMPTZ   | NOT NULL                              | `now()` |
| `updated_at`| TIMESTAMPTZ   | NOT NULL                              | `now()` |

**Unique constraint:** `(tenant_id, name, channel)`  
**Channels:** `EMAIL`, `SMS`, `SLACK`, `PUSH`

---

### 2.7 `notifications` (V005) — Hypertable

Individual notification delivery records.

| Column         | Type          | Constraints                       | Default     |
|---------------|---------------|-----------------------------------|-------------|
| `id`          | UUID          | COMPOSITE PK (id, created_at)     | uuid_generate_v4() |
| `tenant_id`   | UUID          | NOT NULL, FK -> tenants           |             |
| `template_id` | UUID          | FK -> notification_templates      |             |
| `channel`     | VARCHAR(20)   | NOT NULL                          |             |
| `recipient`   | VARCHAR(500)  | NOT NULL                          |             |
| `subject`     | VARCHAR(500)  |                                   |             |
| `body`        | TEXT          |                                   |             |
| `status`      | VARCHAR(20)   | NOT NULL                          | `'PENDING'` |
| `priority`    | INT           | NOT NULL                          | `5`         |
| `retry_count` | INT           | NOT NULL                          | `0`         |
| `max_retries` | INT           | NOT NULL                          | `3`         |
| `error_message`| TEXT         |                                   |             |
| `metadata`    | JSONB         |                                   | `'{}'`      |
| `scheduled_at`| TIMESTAMPTZ   |                                   |             |
| `sent_at`     | TIMESTAMPTZ   |                                   |             |
| `delivered_at`| TIMESTAMPTZ   |                                   |             |
| `created_at`  | TIMESTAMPTZ   | COMPOSITE PK, partition key       | `now()`     |

**Hypertable config:** chunk interval = 7 days  
**Retention:** 90 days auto-drop  
**Statuses:** `PENDING`, `SENT`, `DELIVERED`, `FAILED`, `CANCELLED`

---

### 2.8 `job_queue` (V006)

Background job queue with priority, locking, and retry support.

| Column            | Type          | Constraints                | Default     |
|------------------|---------------|----------------------------|-------------|
| `id`             | UUID          | PRIMARY KEY                | uuid_generate_v4() |
| `tenant_id`      | UUID          | NOT NULL, FK -> tenants    |             |
| `queue_name`     | VARCHAR(100)  | NOT NULL                   | `'default'` |
| `job_type`       | VARCHAR(255)  | NOT NULL                   |             |
| `payload`        | JSONB         | NOT NULL                   |             |
| `priority`       | INT           | NOT NULL                   | `5`         |
| `status`         | VARCHAR(20)   | NOT NULL                   | `'PENDING'` |
| `max_retries`    | INT           | NOT NULL                   | `3`         |
| `retry_count`    | INT           | NOT NULL                   | `0`         |
| `next_retry_at`  | TIMESTAMPTZ   |                            |             |
| `locked_by`      | VARCHAR(255)  |                            |             |
| `locked_at`      | TIMESTAMPTZ   |                            |             |
| `lock_expires_at`| TIMESTAMPTZ   |                            |             |
| `started_at`     | TIMESTAMPTZ   |                            |             |
| `completed_at`   | TIMESTAMPTZ   |                            |             |
| `error_message`  | TEXT          |                            |             |
| `result`         | JSONB         |                            |             |
| `timeout_seconds`| INT           |                            | `3600`      |
| `metadata`       | JSONB         |                            | `'{}'`      |
| `version`        | BIGINT        | NOT NULL                   | `0`         |
| `created_at`     | TIMESTAMPTZ   | NOT NULL                   | `now()`     |
| `updated_at`     | TIMESTAMPTZ   | NOT NULL                   | `now()`     |

**Indexes:**  
- `idx_queue_status_prio` — partial on `(status, priority DESC, created_at ASC)` WHERE status IN `('PENDING','RETRY')`
- `idx_queue_locked` — partial on `(locked_by, lock_expires_at)` WHERE `locked_by IS NOT NULL`
- `idx_queue_next_retry` — partial on `(next_retry_at)` WHERE `status = 'RETRY'`
- `idx_queue_name` — on `(queue_name, status)`

**Statuses:** `PENDING`, `LOCKED`, `RUNNING`, `SUCCESS`, `FAILED`, `RETRY`, `DEAD_LETTER`  
**Priority:** 1 (highest) to 10 (lowest), default 5

---

### 2.9 `job_queue_dead_letter` (V006)

Permanently failed queue items moved here after exhausting retries.

| Column         | Type          | Constraints              | Default     |
|---------------|---------------|--------------------------|-------------|
| `id`          | UUID          | PRIMARY KEY              | uuid_generate_v4() |
| `original_id` | UUID          | NOT NULL                 |             |
| `tenant_id`   | UUID          | NOT NULL, FK -> tenants  |             |
| `queue_name`  | VARCHAR(100)  | NOT NULL                 |             |
| `job_type`    | VARCHAR(255)  | NOT NULL                 |             |
| `payload`     | JSONB         | NOT NULL                 |             |
| `error_message`| TEXT         |                          |             |
| `retry_count` | INT           | NOT NULL                 |             |
| `metadata`    | JSONB         |                          | `'{}'`      |
| `failed_at`   | TIMESTAMPTZ   | NOT NULL                 | `now()`     |

**Index:** `idx_dlq_tenant (tenant_id, failed_at DESC)`

---

## 3. TimescaleDB Hypertables

Three tables are converted to TimescaleDB hypertables for efficient time-series
queries, automatic partitioning, and transparent chunk management:

```
+---------------------+------------------+-----------+
| Table               | Partition Column | Chunk     |
+---------------------+------------------+-----------+
| job_executions      | started_at       | 7 days    |
| alert_history       | triggered_at     | 7 days    |
| notifications       | created_at       | 7 days    |
+---------------------+------------------+-----------+
```

**Benefits:**
- Automatic time-based partitioning (no manual table management)
- Transparent query optimization across chunks
- Efficient INSERT for high-volume time-series data
- Compression support for older chunks (future optimization)

---

## 4. Continuous Aggregates

### `job_execution_hourly`

Pre-computed hourly rollups of job execution metrics:

```sql
SELECT
    job_id,
    tenant_id,
    time_bucket('1 hour', started_at) AS bucket,
    COUNT(*)                          AS total_runs,
    COUNT(*) FILTER (WHERE status = 'SUCCESS')  AS success_count,
    COUNT(*) FILTER (WHERE status = 'FAILED')   AS failure_count,
    AVG(duration_ms)                  AS avg_duration_ms,
    MAX(duration_ms)                  AS max_duration_ms,
    MIN(duration_ms)                  AS min_duration_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms)
                                      AS p95_duration_ms
FROM job_executions
GROUP BY job_id, tenant_id, bucket
```

**Refresh policy:** every 1 hour, covering 3-hour lookback with 1-hour end offset

---

## 5. Retention Policies

```
+---------------------+-----------+-----------------------------------------+
| Table               | Retention | Rationale                               |
+---------------------+-----------+-----------------------------------------+
| job_executions      | 90 days   | Raw execution data; aggregates survive  |
| alert_history       | 180 days  | Compliance audit trail, longer window   |
| notifications       | 90 days   | Delivery records; templates survive     |
+---------------------+-----------+-----------------------------------------+
```

Retention policies run automatically via TimescaleDB background workers.
Continuous aggregates (`job_execution_hourly`) are **not** affected by raw
data retention — historical rollup data persists indefinitely.

---

## 6. Index Strategy

```
Standard Tables (B-tree):
+-------------------+----------------------------------------------+
| Table             | Indexes                                      |
+-------------------+----------------------------------------------+
| tenants           | slug, status                                 |
| jobs              | tenant_id, status, tags (GIN)                |
| alert_rules       | tenant_id, job_id, enabled                   |
| job_queue         | tenant_id, queue_name+status                 |
| job_queue_dead_ltr| tenant_id+failed_at                          |
| notif_templates   | tenant_id                                    |
+-------------------+----------------------------------------------+

Hypertable Tables (B-tree, time-ordered):
+-------------------+----------------------------------------------+
| Table             | Indexes                                      |
+-------------------+----------------------------------------------+
| job_executions    | (job_id, started_at DESC)                    |
|                   | (tenant_id, started_at DESC)                 |
|                   | (status, started_at DESC)                    |
| alert_history     | (alert_rule_id, triggered_at DESC)           |
|                   | (tenant_id, triggered_at DESC)               |
|                   | (status, triggered_at DESC)                  |
| notifications     | (tenant_id, created_at DESC)                 |
|                   | (status, created_at DESC)                    |
|                   | (channel, created_at DESC)                   |
+-------------------+----------------------------------------------+

Partial Indexes (for queue polling performance):
+-------------------+----------------------------------------------+
| job_queue         | (status, priority DESC, created_at ASC)      |
|                   |   WHERE status IN ('PENDING','RETRY')        |
|                   | (next_retry_at)                              |
|                   |   WHERE status = 'RETRY'                     |
|                   | (locked_by, lock_expires_at)                 |
|                   |   WHERE locked_by IS NOT NULL                |
+-------------------+----------------------------------------------+
```

---

## 7. Migration Inventory

| Version | File                                   | Tables Created                      | Notes                           |
|---------|----------------------------------------|-------------------------------------|---------------------------------|
| V001    | `V001__create_tenants_table.sql`       | `tenants`                           | uuid-ossp + timescaledb enabled |
| V002    | `V002__create_jobs_table.sql`          | `jobs`                              | GIN index on JSONB tags         |
| V003    | `V003__create_job_executions_hypertable.sql` | `job_executions`             | Hypertable + continuous agg     |
| V004    | `V004__create_alert_rules_table.sql`   | `alert_rules`, `alert_history`      | alert_history is hypertable     |
| V005    | `V005__create_notifications_table.sql` | `notification_templates`, `notifications` | notifications is hypertable |
| V006    | `V006__create_job_queue_table.sql`     | `job_queue`, `job_queue_dead_letter`| Partial indexes for polling     |

**Total:** 10 tables, 3 hypertables, 1 continuous aggregate, 3 retention policies, 1 shared trigger function
