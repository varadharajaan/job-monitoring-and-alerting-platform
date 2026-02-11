# Architecture Document

> **Job Monitoring & Alerting Platform** — Enterprise-grade, multi-tenant system  
> Version 1.0.0-SNAPSHOT | Last updated: 2026-02-10

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Module Dependency Graph](#3-module-dependency-graph)
4. [Service Topology & Ports](#4-service-topology--ports)
5. [Request Flow — Job Registration](#5-request-flow--job-registration)
6. [Event Flow — Alert Lifecycle](#6-event-flow--alert-lifecycle)
7. [Notification Dispatch Flow](#7-notification-dispatch-flow)
8. [Background Job Queue Flow](#8-background-job-queue-flow)
9. [Infrastructure Stack](#9-infrastructure-stack)
10. [Data Flow Overview](#10-data-flow-overview)
11. [Multi-Tenancy Model](#11-multi-tenancy-model)
12. [Configuration Architecture](#12-configuration-architecture)
13. [Error Handling Architecture](#13-error-handling-architecture)
14. [Observability Stack](#14-observability-stack)
15. [Security Architecture](#15-security-architecture)
16. [Technology Decisions](#16-technology-decisions)
17. [Service Discovery Architecture](#17-service-discovery-architecture)
18. [Azure Dual-Cloud Architecture](#18-azure-dual-cloud-architecture)
19. [Scheduled Tasks & Background Processing](#19-scheduled-tasks--background-processing)

---

## 1. System Overview

The platform unifies five enterprise concerns into a single Gradle multi-module
monorepo:

| Concern               | Status     | Description                                    |
|-----------------------|------------|----------------------------------------------- |
| Job Monitoring        | Active     | Cron monitoring, SLA tracking, execution audit |
| Multi-Channel Alerts  | Active     | Rule engine, Kafka-driven event evaluation     |
| Notification Dispatch | Active     | Email/SMS/Slack/Push/Webhook with templates    |
| Background Job Queue  | Active     | Priority queue, retries, dead-letter, stats    |
| Service Discovery     | Active     | Spring Cloud Netflix Eureka                    |
| Azure Cloud Ready     | Active     | Dual-cloud: AWS prod + Azure profiles          |
| Log Ingestion         | Active     | Elasticsearch log search, alert patterns, retention |
| DB Performance        | Active     | Slow query detection, index suggestions, EXPLAIN |

---

## 2. High-Level Architecture

```
+-----------------------------------------------------------------------------------+
|                                  CLIENTS                                          |
|                    (Web UI / CLI / External Schedulers / APIs)                    |
+--------------------------------------+--------------------------------------------+
                                       |
                                       | HTTPS (REST/JSON)
                                       v
+-----------------------------------------------------------------------------------+
|                          INGESTION GATEWAY (:8080)                                |
|                     Rate Limiting (Bucket4j) + Request Routing                    |
+------+----------+----------+----------+----------+--------------------------------+
       |          |          |          |          |
       v          v          v          v          v
+----------+ +----------+ +----------+ +----------+ +----------+  +----------+
|  AUTH    | |  JOB     | | ALERTING | | NOTIF    | | JOB      |  | EUREKA   |
|  SERVICE | | MONITOR  | | SERVICE  | | SERVICE  | | QUEUE    |  | SERVER   |
|  :8081   | |  :8082   | |  :8083   | |  :8084   | | SERVICE  |  |  :8761   |
|          | |          | |          | |          | |  :8085   |  |          |
| JWT Auth | | Exec     | | Kafka    | | Email    | | Priority |  | Service  |
| Token    | | Tracking | | Consumer | | Slack    | | Dispatch |  | Registry |
| Refresh  | | SLA Eval | | Rules    | | SMS/Push | | Stats    |  | Heartbeat|
|          | | Heartbeat| | Escalate | | Webhook  | | Locking  |  |          |
|          | | Retry    | |          | |          | | Cleanup  |  |          |
+----+-----+ +----+-----+ +----+-----+ +----+-----+ +----+-----+  +----------+
     |             |            |            |            |
     +------+------+------+-----+------+-----+------+----+
            |             |            |             |
            v             v            v             v
+-----------------------------------------------------------------------------------+
|                         KAFKA EVENT BUS (KRaft 3.7.0)                             |
|                                                                                   |
|  +----------------+ +------------------+ +-----------------+ +------------------+ |
|  | job-events     | | alert-events     | | notification-   | | queue-events     | |
|  | (6 partitions) | | (6 partitions)   | | events (6 part) | | (6 partitions)   | |
|  +----------------+ +------------------+ +-----------------+ +------------------+ |
|  +----------------+                                                               |
|  | dead-letter    |                                                               |
|  | (6 partitions) |                                                               |
|  +----------------+                                                               |
+-----+----------------------------------+-----------------------------------------+
      |                                  |
      v                                  v
+---------------------+    +-------------------------+
|    JOB WORKER       |    |  NOTIFICATION WORKER    |
|    (headless)       |    |  (headless)             |
|                     |    |                         |
| Kafka Consumer      |    | Kafka Consumer          |
| Job Processing      |    | SMTP / AWS SES / SNS    |
| Retry + Backoff     |    | Delivery + Tracking     |
+----------+----------+    +----------+--------------+
           |                          |
           +----------+---------------+
                      |
                      v
+-----------------------------------------------------------------------------------+
|                          PERSISTENCE & CACHING                                    |
|                                                                                   |
|  +---------------------------+  +-------------------+  +------------------------+ |
|  |   TimescaleDB (pg16)     |  |    Redis 7        |  |    S3 (LocalStack)      | |
|  |                          |  |                   |  |                         | |
|  |  tenants, jobs           |  |  Cache (5 named)  |  |  JSONL log archive      | |
|  |  job_executions*         |  |  Rate-limit state |  |  Athena-queryable       | |
|  |  alert_rules             |  |  Session tokens   |  |  Partitioned by date    | |
|  |  alert_history*          |  |                   |  |                         | |
|  |  notifications*          |  +-------------------+  +------------------------+  |
|  |  notification_templates  |                                                     |
|  |  job_queue               |                                                     |
|  |  job_queue_dead_letter   |                                                     |
|  |  (* = hypertable)        |                                                     |
|  +---------------------------+                                                    |
+-----------------------------------------------------------------------------------+
|                          OBSERVABILITY                                            |
|  +-------------------+  +-------------------+  +-------------------------------+  |
|  | Prometheus :9090  |  | Grafana :3000     |  | Structured Logging (JSONL)    |  |
|  | Metric scraping   |  | Dashboards        |  | MDC: traceId/tenantId/userId  |  |
|  +-------------------+  +-------------------+  +-------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 3. Module Dependency Graph

```
settings.gradle
    |
    +-- platform/common          (Java library JAR — NOT bootable)
    |       |
    |       +-- config/           PlatformProperties, AsyncConfig, KafkaConfig,
    |       |                     JacksonConfig, RedisConfig, LoggingProperties
    |       +-- exception/        ErrorCode enum, BusinessException hierarchy,
    |       |                     GlobalExceptionHandler, ApiError
    |       +-- dto/              ApiResponse<T>, PageResponse<T>
    |       +-- event/            PlatformEvent, JobEvent, AlertEvent,
    |       |                     NotificationEvent, QueueEvent
    |       +-- model/            BaseEntity (UUID, audit, @Version, tenantId)
    |       +-- logging/          MdcLoggingFilter, S3LogUploader, S3LogConfig
    |       +-- resources/        logback-platform.xml, application-platform-defaults.yml
    |
    +-- platform/schema           (Java library JAR — Flyway migrations only)
    |       +-- db/migration/     V001-V006 SQL scripts for TimescaleDB
    |
    +-- services/
    |       +-- config-server     (:8888) Spring Cloud Config Server
    |       +-- eureka-server     (:8761) Service Discovery Registry
    |       +-- auth-service      (:8081) JWT authentication
    |       +-- ingestion-gateway (:8080) API gateway + rate limiting
    |       +-- job-monitoring    (:8082) Core job monitoring + SLA evaluation
    |       |                             + SLA scheduler, heartbeat monitor
    |       |                             + retry engine (exponential backoff)
    |       +-- alerting-service  (:8083) Alert rule engine + Kafka consumer
    |       +-- notification-svc  (:8084) Multi-channel dispatch (5 channels)
    |       +-- job-queue-service (:8085) Background job queue + stats + cleanup
    |
    +-- workers/
            +-- job-worker        (headless) Background job processor
            +-- notification-wkr  (headless) Notification delivery agent
```

**Dependency Direction:**

```
+-------------------+     +-------------------+
| All Services      |---->| platform/common   |
| All Workers       |---->| (compile dep)     |
+-------------------+     +---------+---------+
                                    |
+-------------------+               |
| All Services      |---->+---------+---------+
| (with DB access)  |     | platform/schema   |
+-------------------+     | (runtime dep)     |
                          +-------------------+
```

---

## 4. Service Topology & Ports

```
+------+------------------------------------+--------+-------------------+
| Type | Service                            |  Port  | Kafka Group       |
+------+------------------------------------+--------+-------------------+
| DISC | eureka-server                      |  8761  | --                |
| INFRA| config-server                      |  8888  | --                |
| INFRA| auth-service                       |  8081  | --                |
| GW   | ingestion-gateway                  |  8080  | --                |
| CORE | job-monitoring-service             |  8082  | job-monitoring    |
| CORE | alerting-service                   |  8083  | alerting          |
| CORE | notification-service               |  8084  | notification      |
| CORE | job-queue-service                  |  8085  | job-queue         |
| CORE | log-ingestion-service              |  8086  | log-ingestion     |
| CORE | db-performance-service             |  8087  | --                |
| WORK | job-worker (headless)              |  --    | job-worker        |
| WORK | notification-worker (headless)     |  --    | notification-wkr  |
+------+------------------------------------+--------+-------------------+

Infrastructure containers:
+------+------------------------------------+--------+
| DB   | TimescaleDB (PostgreSQL 16)        |  5432  |
| CACHE| Redis 7                            |  6379  |
| MSG  | Apache Kafka 3.7.0 (KRaft)         |  9092  |
| CLOUD| LocalStack (S3)                    |  4566  |
| SRCH | Elasticsearch 8.13.0               |  9200  |
| MON  | Prometheus                         |  9090  |
| MON  | Grafana                            |  3000  |
+------+------------------------------------+--------+

Azure cloud equivalents (activated via `--spring.profiles.active=azure`):
+------+------------------------------------+-----------------------------------+
| DB   | Azure Database for PostgreSQL     | Flexible Server, SSL required      |
| CACHE| Azure Cache for Redis              | SSL, port 6380                    |
| MSG  | Azure Event Hubs                   | Kafka-compatible, SASL_SSL/PLAIN  |
| CLOUD| Azure Blob Storage                 | Conditional via platform.azure.*  |
| DISC | Eureka Server                      | Auto-enabled in azure profile     |
+------+------------------------------------+-----------------------------------+
```

---

## 5. Request Flow — Job Registration

```
Client                Gateway           Auth           Job Monitor         Kafka
  |                    :8080            :8081             :8082             |
  |                      |                |                 |               |
  |  POST /api/v1/jobs   |                |                 |               |
  |--------------------->|                |                 |               |
  |                      |  Validate JWT  |                 |               |
  |                      |--------------->|                 |               |
  |                      | 200 OK (claims)|                 |               |
  |                      |<---------------|                 |               |
  |                      |                                  |               |
  |                      |  Rate-limit check (Bucket4j)     |               |
  |                      |--+                               |               |
  |                      |  | (pass)                        |               |
  |                      |<-+                               |               |
  |                      |                                  |               |
  |                      |  Forward POST /api/v1/jobs       |               |
  |                      |--------------------------------->|               |
  |                      |                                  |               |
  |                      |                  Validate + persist to DB        |
  |                      |                                  |--+            |
  |                      |                                  |  |            |
  |                      |                                  |<-+            |
  |                      |                                  |               |
  |                      |                  Publish JobEvent(REGISTERED)    |
  |                      |                                  |-------------->|
  |                      |                                  |               |
  |                      |  201 Created (ApiResponse<Job>)  |               |
  |                      |<---------------------------------|               |
  |  201 Created         |                                  |               |
  |<---------------------|                                  |               |
  |                      |                                  |               |
```

---

## 6. Event Flow — Alert Lifecycle

```
+-------------------+         +-------------------+        +--------------------+
| job-monitoring    |         | alerting-service  |        | notification-svc   |
| :8082             |         | :8083             |        | :8084              |
+--------+----------+         +--------+----------+        +--------+-----------+
         |                             |                            |
         |  JobEvent(FAILED)           |                            |
         |---------------------------->|                            |
         |                             |                            |
         |               Evaluate alert rules                       |
         |               against failure context                    |
         |                             |--+                         |
         |                             |  | Match found             |
         |                             |<-+                         |
         |                             |                            |
         |                             |  AlertEvent(TRIGGERED)     |
         |                             |--------------------------->|
         |                             |                            |
         |                             |              Resolve channels from rule
         |                             |              (EMAIL, SLACK, SMS, PUSH)
         |                             |                            |--+
         |                             |                            |  |
         |                             |                            |<-+
         |                             |                            |
         |                             |              NotificationEvent(REQUESTED)
         |                             |                            |----+
         |                             |                            |    | (to Kafka)
         |                             |                            |<---+
         |                             |                            |
         |                             |                            v
         |                     +-------+--------+       +-----------+----------+
         |                     | alert_history  |       | notification-worker  |
         |                     | (TimescaleDB)  |       | (headless)           |
         |                     +----------------+       |                      |
         |                                              | SMTP / SES / SNS    |
         |                                              | Slack Webhook        |
         |                                              +----------------------+
```

---

## 7. Notification Dispatch Flow

```
+-------------------+        +---------+       +---------------------+
| Any Service       |        |  Kafka  |       | notification-worker |
| publishes         |        |         |       | (headless)          |
| NotificationEvent |------->| notif-  |------>|                     |
+-------------------+        | events  |       +----------+----------+
                             +---------+                  |
                                                          |
                            +-----------------------------+-----------------------------+
                            |                             |                             |
                            v                             v                             v
                   +--------+--------+           +--------+--------+           +--------+--------+
                   |   EMAIL         |           |   SLACK         |           |   SMS / |PUSH    |
                   |                 |           |                 |           |         |           |
                   | Template render |           | Webhook POST    |           | AWS     |  SNS         |
                   | SMTP / AWS SES  |           | Markdown format |           | AWS     |    SES         |
                   +---------+-------+           +--------+--------+           +--------+--------+
                             |                            |                             |
                             +----------------------------+-----------------------------+
                                                          |
                                                          v
                                               +----------+----------+
                                               |  notifications      |
                                               |  table (hypertable) |
                                               |  status tracking    |
                                               +---------------------+
```

---

## 8. Background Job Queue Flow

```
+----------+     +-------------------+     +-----------+     +-----------------+
| Client   |     | job-queue-service |     |   Kafka   |     |   job-worker    |
| API call |     |     :8085         |     |           |     |   (headless)    |
+----+-----+     +--------+----------+     +-----+-----+     +-------+---------+
     |                     |                      |                   |
     | POST /queue/jobs    |                      |                   |
     |-------------------->|                      |                   |
     |                     |                      |                   |
     |      Validate + insert into job_queue      |                   |
     |      (status=PENDING, priority=N)          |                   |
     |                     |--+                   |                   |
     |                     |  |                   |                   |
     |                     |<-+                   |                   |
     |                     |                      |                   |
     |                     | QueueEvent(SUBMITTED)|                   |
     |                     |--------------------->|                   |
     |                     |                      |                   |
     | 202 Accepted        |                      |                   |
     |<--------------------|                      |                   |
     |                     |                      | QueueEvent        |
     |                     |                      |------------------>|
     |                     |                      |                   |
     |                     |           Poll + lock job (SELECT FOR UPDATE SKIP LOCKED)
     |                     |                      |                   |--+
     |                     |                      |                   |  |
     |                     |                      |                   |<-+
     |                     |                      |                   |
     |                     |                      |  Execute job      |
     |                     |                      |                   |--+
     |                     |                      |                   |  |
     |                     |                      |                   |<-+
     |                     |                      |                   |
     |                     |                      | QueueEvent        |
     |                     |                      | (COMPLETED/FAILED)|
     |                     |                      |<------------------|
     |                     |                      |                   |
     |                     | Update job_queue     |                   |
     |                     | status + result      |                   |
     |                     |<---------------------|                   |
     |                     |                      |                   |

On FAILURE with retries remaining:
     |                     |                      |                   |
     |                     | Move to RETRY status, calculate next_retry_at
     |                     | using exponential backoff (multiplier=2.0)
     |                     |                      |                   |

After max retries exhausted:
     |                     |                      |                   |
     |                     | Copy to job_queue_dead_letter            |
     |                     | Original row -> status=DEAD_LETTER       |
     |                     |                      |                   |
```

---

## 9. Infrastructure Stack

```
+-----------------------------------------------------------------------------------+
|                            Docker Compose Stack                                   |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  +---------------------------+     +---------------------------+                  |
|  | timescale/timescaledb    |     | apache/kafka:3.7.0        |                   |
|  | :latest-pg16             |     | (KRaft mode, no ZK)       |                   |
|  |                          |     |                           |                   |
|  | Port: 5432               |     | Port: 9092 (broker)       |                   |
|  | DB: jobmonitor           |     | Port: 9093 (controller)   |                   |
|  | User: jobmonitor         |     | Replication factor: 1     |                   |
|  | Extensions:              |     | Cluster: MkU3OEV...       |                   |
|  |   uuid-ossp              |     +---------------------------+                   |
|  |   timescaledb            |                                                     |
|  +---------------------------+     +---------------------------+                  |
|                                    | redis:7-alpine            |                  |
|  +---------------------------+     |                           |                  |
|  | localstack/localstack:3   |     | Port: 6379                |                  |
|  |                           |     | MaxMem: 256mb             |                  |
|  | Port: 4566                |     | Policy: allkeys-lru       |                  |
|  | Services: s3              |     +---------------------------+                  |
|  | Region: us-east-1         |                                                    |
|  +---------------------------+     +---------------------------+                  |
|                                    | prom/prometheus           |                  |
|  +---------------------------+     |                           |                  |
|  | grafana/grafana           |     | Port: 9090                |                  |
|  |                           |     | Scrapes /actuator/prom    |                  |
|  | Port: 3000                |     | every 15s                 |                  |
|  | Admin pass: admin         |     +---------------------------+                  |
|  +---------------------------+                                                    |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 10. Data Flow Overview

```
                                WRITE PATH
                                ==========

  Client Request
       |
       v
  +----+----+     +---------+     +-----------+
  | Gateway | --> | Service | --> | Postgres  |  (synchronous write)
  +---------+     +----+----+     | /Timescale|
                       |          +-----------+
                       |
                       v
                  +----+----+
                  |  Kafka  |  (async event publish)
                  +----+----+
                       |
              +--------+--------+
              |                 |
              v                 v
        +-----+------+   +-----+------+
        | Worker(s)  |   | Alerting   |  (async consumers)
        +-----+------+   +-----+------+
              |                 |
              v                 v
        +-----+------+   +-----+------+
        | DB update  |   | Alert +    |
        | (result)   |   | Notif      |
        +------------+   +------------+


                                READ PATH
                                =========

  Client Request
       |
       v
  +----+----+     +---------+     +-----------+
  | Gateway | --> | Service | --> | Redis     |  (cache hit?)
  +---------+     +----+----+     +-----+-----+
                       |                |
                       |  cache miss    |
                       v                |
                  +----+--------+       |
                  | Postgres /  | <-----+
                  | Timescale   |  (populate cache)
                  +-------------+
```

---

## 11. Multi-Tenancy Model

```
+-------------------------------------------------------------------+
|                       Multi-Tenancy Architecture                  |
+-------------------------------------------------------------------+
|                                                                   |
|  +-------------------+                                            |
|  |    HTTP Request   |                                            |
|  | X-Tenant-Id: abc  |                                            |
|  +--------+----------+                                            |
|           |                                                       |
|           v                                                       |
|  +--------+----------+                                            |
|  |  MdcLoggingFilter |  Extracts tenantId -> MDC                  |
|  |  (OncePerRequest) |  Extracts traceId  -> MDC                  |
|  +--------+----------+  Extracts userId   -> MDC                  |
|           |                                                       |
|           v                                                       |
|  +--------+----------+     +-----------------------------------+  |
|  |  Service Layer    |     |  Database Schema                  |  |
|  |                   |     |                                   |  |
|  |  Every entity has |---->|  Every table has tenant_id (FK)   |  |
|  |  tenantId via     |     |  Indexes include tenant_id        |  |
|  |  BaseEntity       |     |  Unique constraints are           |  |
|  +-------------------+     |  (tenant_id, name) scoped         |  |
|                            +-----------------------------------+  |
|                                                                   |
|  Isolation strategy: Shared database, tenant-discriminator column |
|  Tenant table: id, name, slug, plan (FREE/PRO/ENTERPRISE), status |
+-------------------------------------------------------------------+
```

---

## 12. Configuration Architecture

```
+------------------------------------------------------------------+
|                     Configuration Hierarchy                       |
+------------------------------------------------------------------+
|                                                                   |
|  Priority (highest to lowest):                                    |
|                                                                   |
|  1. Environment Variables        ${KAFKA_BOOTSTRAP:localhost:9092}|
|  2. application-{profile}.yml   (per-env overrides)               |
|  3. application.yml             (per-service defaults)            |
|  4. application-platform-defaults.yml (platform shared)           |
|                                                                   |
+------------------------------------------------------------------+
|                                                                   |
|  +----------------------------+                                   |
|  | PlatformProperties.java   |   @ConfigurationProperties         |
|  | (prefix = "platform")     |   prefix: platform.*               |
|  +----------------------------+                                   |
|  |                            |                                   |
|  |  platform.async            |   3 thread pool configs           |
|  |    .taskPool               |   core/max/queue/prefix/timeout   |
|  |    .notificationPool       |                                   |
|  |    .jobQueuePool           |                                   |
|  |                            |                                   |
|  |  platform.kafka            |                                   |
|  |    .topics                 |   6 Kafka topics                  |
|  |    .listener.concurrency   |   Consumer concurrency            |
|  |    .errorHandling          |   Backoff + max retries           |
|  |                            |                                   |
|  |  platform.cache            |                                   |
|  |    .defaultTtl             |   15 minutes                      |
|  |    .ttls (per-cache map)   |   jobs=5m, templates=1h, etc      |
|  |                            |                                   |
|  |  platform.s3               |   bucket, region, endpoint, cron  |
|  |  platform.notification     |   Rate limits per channel         |
|  |  platform.jobMonitor       |   SLA cron, retries, retention    |
|  |  platform.jobQueue         |   Workers, poll, lock timeout     |
|  |  platform.security         |   Public + admin path patterns    |
|  +----------------------------+                                   |
|                                                                   |
|  ZERO hardcoded values. Every tunable is externalized.            |
+------------------------------------------------------------------+
```

---

## 13. Error Handling Architecture

```
+------------------------------------------------------------------+
|                   Centralized Error Code Enum                    |
+------------------------------------------------------------------+
|                                                                   |
|  ErrorCode (22 values, each with HttpStatus + defaultMessage)     |
|                                                                   |
|  +-------------------+-------------------+-----------------------+|
|  | Category          | Code              | HTTP Status           ||
|  +-------------------+-------------------+-----------------------+|
|  | Business          | BUSINESS_ERROR    | 400 Bad Request       ||
|  | Resource          | RESOURCE_NOT_FOUND| 404 Not Found         ||
|  | Resource          | DUPLICATE_RESOURCE| 409 Conflict          ||
|  | Rate Limit        | RATE_LIMIT_EXCEED | 429 Too Many Requests ||
|  | Auth              | UNAUTHORIZED      | 401 Unauthorized      ||
|  | Auth              | FORBIDDEN         | 403 Forbidden         ||
|  | Auth              | AUTH_FAILED       | 401 Unauthorized      ||
|  | Auth              | ACCESS_DENIED     | 403 Forbidden         ||
|  | Availability      | SERVICE_UNAVAIL   | 503 Service Unavail   ||
|  | Availability      | REQUEST_TIMEOUT   | 408 Request Timeout   ||
|  | Concurrency       | OPTIMISTIC_LOCK   | 409 Conflict          ||
|  | Validation        | VALIDATION_FAILED | 400 Bad Request       ||
|  | Validation        | BINDING_FAILED    | 400 Bad Request       ||
|  | Validation        | CONSTRAINT_VIOL   | 400 Bad Request       ||
|  | Validation        | MISSING_PARAMETER | 400 Bad Request       ||
|  | Request           | MALFORMED_REQUEST | 400 Bad Request       ||
|  | Request           | TYPE_MISMATCH     | 400 Bad Request       ||
|  | HTTP              | METHOD_NOT_ALLOW  | 405 Method Not Allowed||
|  | HTTP              | UNSUPPORTED_MEDIA | 415 Unsupported Media ||
|  | HTTP              | ENDPOINT_NOT_FOUND| 404 Not Found         ||
|  | Data              | DATA_INTEGRITY    | 409 Conflict          ||
|  | Catch-all         | INTERNAL_ERROR    | 500 Internal Error    ||
|  +-------------------+-------------------+-----------------------+|
+------------------------------------------------------------------+

Exception Hierarchy:                                                               
  RuntimeException                                                  
       |                                                            
  BusinessException (status, ErrorCode)                             
       |                                                            
       +-- ResourceNotFoundException                                
       +-- DuplicateResourceException                               
       +-- RateLimitExceededException                               
       +-- UnauthorizedException                                    
       +-- ForbiddenException                                       
       +-- ServiceUnavailableException                              
       +-- OptimisticLockException                                  
       +-- RequestTimeoutException                                  

GlobalExceptionHandler resolution order:
  1. Specific BusinessException subclass handlers
  2. Generic BusinessException handler
  3. Spring Security exceptions (AuthenticationException, AccessDeniedException)
  4. Validation exceptions (MethodArgumentNotValid, Bind, ConstraintViolation)
  5. HTTP exceptions (NotReadable, TypeMismatch, MethodNotAllowed)
  6. JPA exceptions (OptimisticLocking, DataIntegrity)
  7. Catch-all (Exception.class -> INTERNAL_ERROR)

Every response follows ApiError JSON:
  {
    "timestamp": "2026-02-10T12:00:00Z",
    "status": 404,
    "error": "Not Found",
    "errorCode": "RESOURCE_NOT_FOUND",
    "message": "Job not found with id: abc-123",
    "path": "/api/v1/jobs/abc-123",
    "traceId": "a1b2c3d4",
    "fieldErrors": null
  }
```

---

## 14. Observability Stack

```
+------------------------------------------------------------------+
|                       Observability Pipeline                     |
+------------------------------------------------------------------+
|                                                                   |
|  METRICS                                                          |
|  +----------+     +-----------+     +------------+                |
|  | Service  |     | Prometheus|     | Grafana    |                |
|  | /actuator| --> | :9090     | --> | :3000      |                |
|  | /prom    |     | (15s pull)|     | Dashboards |                |
|  +----------+     +-----------+     +------------+                |
|                                                                   |
|  LOGGING                                                          |
|  +----------+     +-----------+     +------------+                |
|  | Service  |     | logback   |     | Local      |                |
|  | (Slf4j)  | --> | JSONL     | --> | File       | --------+      |
|  | + MDC    |     | appender  |     | (.jsonl.gz)|         |      |
|  +----------+     +-----------+     +------------+         |      |
|                                                            v      |
|  MDC fields per request:                          +--------+---+  |
|    traceId    (UUID or header)                    | S3 Bucket  |  |
|    tenantId   (X-Tenant-Id header)                | (hourly    |  |
|    userId     (JWT claim)                         |  upload)   |  |
|    method     (GET/POST/...)                      +--------+---+  |
|    uri        (/api/v1/jobs)                               |      |
|                                                            v      |
|                                                    +-------+---+  |
|                                                    | Athena    |  |
|                                                    | Queries   |  |
|                                                    +-----------+  |
+------------------------------------------------------------------+
```

---

## 15. Security Architecture

```
+------------------------------------------------------------------+
|                       Security Layers                            |
+------------------------------------------------------------------+
|                                                                  |
|  Layer 1: API Gateway                                            |
|  +--------------------------------------------------------------+|
|  | Bucket4j rate limiting: 60 req/min, burst 10                 ||
|  | All requests routed through :8080                            ||
|  +--------------------------------------------------------------+|
|                                                                  |
|  Layer 2: JWT Authentication (auth-service :8081)                |
|  +--------------------------------------------------------------+|
|  | Access token:  1 hour expiry, signed with HMAC-SHA            |
|  | Refresh token: 24 hour expiry                                 |
|  | Claims: userId, tenantId, roles                               |
|  +--------------------------------------------------------------+|
|                                                                  |
|  Layer 3: Path-based Authorization                               |
|  +--------------------------------------------------------------+|
|  | Public paths (no auth required):                              |
|  |   /api-docs/**, /swagger-ui/**, /actuator/health/**           |
|  |                                                               |
|  | Admin paths (ADMIN role required):                            | 
|  |   /actuator/**                                                |
|  |                                                               |
|  | All other paths: authenticated + tenant-scoped                |
|  +--------------------------------------------------------------+|
|                                                                  |
|  Layer 4: Tenant Isolation                                       |
|  +--------------------------------------------------------------+|
|  | Every DB query scoped by tenant_id                            |
|  | Every Kafka event carries tenantId                            |
|  | MDC propagates tenantId across async boundaries               |
|  +--------------------------------------------------------------+|
+------------------------------------------------------------------+
```

---

## 16. Technology Decisions

| Decision                  | Choice                    | Rationale                                               |
|---------------------------|---------------------------|-------------------------------------------------------- |
| Runtime                   | Java 17                   | LTS, mature ecosystem, team expertise                   |
| Language features         | Java 11 style only        | Broad compatibility, no records/sealed/pattern-matching |
| Framework                 | Spring Boot 3.2.5         | Production-proven, excellent ecosystem                  |
| Build                     | Gradle 8.7                | Faster than Maven, flexible multi-module                |
| Time-series DB            | TimescaleDB (pg16)        | Hypertables, continuous aggregates, retention policies  |
| Message broker            | Kafka 3.7.0 (KRaft)       | High throughput, exactly-once, no ZooKeeper dependency  |
| Cache                     | Redis 7                   | Sub-ms latency, per-cache TTLs, rate limiting           |
| Object storage            | S3 (LocalStack for dev)   | Athena-queryable log archive, cost-effective            |
| Config management         | Spring Cloud Config       | Centralized, profile-based, git-backed                  |
| Service discovery         | Netflix Eureka            | Heartbeat-based, self-preservation, dashboard UI        |
| Auth                      | JWT (jjwt 0.12.5)         | Stateless, scalable, standard claims                    |
| Code generation           | Lombok + MapStruct 1.5.5  | Reduce boilerplate, compile-time mapping                |
| Resilience                | Resilience4j 2.2.0        | Circuit breaker, retry, rate limiter                    |
| API docs                  | SpringDoc 2.5.0           | OpenAPI 3, auto-generated from annotations              |
| Testing                   | JUnit 5 + Testcontainers  | Real infra in tests, no mocking DB/Kafka                |
| Observability             | Micrometer + Prometheus   | Standard metrics, Grafana dashboards                    |
| Container orchestration   | Docker Compose            | Simple local dev, production uses K8 (future)           |
| Cloud (primary)           | AWS (S3, SES, SNS)        | Mature ecosystem, LocalStack for dev                    |
| Cloud (secondary)         | Azure (Event Hubs, Redis, PG, Blob) | Dual-cloud, $200/mo credit tier        |

---

## 17. Service Discovery Architecture

```
+------------------------------------------------------------------+
|                    Eureka Service Discovery                       |
+------------------------------------------------------------------+
|                                                                   |
|  +----------------------------+                                   |
|  | Eureka Server (:8761)      |                                   |
|  | @EnableEurekaServer        |                                   |
|  | HTTP Basic auth            |                                   |
|  | Self-preservation enabled  |                                   |
|  +----------------------------+                                   |
|          ^         ^        ^                                     |
|          |         |        |                                     |
|    register   heartbeat  fetch-registry                           |
|          |         |        |                                     |
|  +-------+---------+--------+------+                              |
|  |       |         |        |      |                              |
|  v       v         v        v      v                              |
|  +------+ +------+ +------+ +------+ +------+ +------+ +------+  |
|  |:8080 | |:8081 | |:8082 | |:8083 | |:8084 | |:8085 | |wrkrs |  |
|  |gate  | |auth  | |job   | |alert | |notif | |queue | |2x    |  |
|  |way   | |svc   | |mon   | |svc   | |svc   | |svc   | |head  |  |
|  +------+ +------+ +------+ +------+ +------+ +------+ +------+  |
|                                                                   |
|  Activation:                                                      |
|  - Disabled by default: eureka.client.enabled=${EUREKA_ENABLED:false} |
|  - Auto-enabled in Azure profile (application-azure.yml)          |
|  - Instance ID: ${spring.application.name}:${random.uuid}        |
|  - Prefer IP address: true                                        |
+------------------------------------------------------------------+
```

---

## 18. Azure Dual-Cloud Architecture

```
+------------------------------------------------------------------+
|              Dual-Cloud Deployment Model                         |
+------------------------------------------------------------------+
|                                                                   |
|  LOCAL / AWS (default profile)        AZURE (azure profile)       |
|  ==============================      ========================    |
|                                                                   |
|  TimescaleDB (Docker)         <-->  Azure DB for PostgreSQL       |
|  Apache Kafka 3.7.0 (Docker)  <-->  Azure Event Hubs (Kafka API) |
|  Redis 7 (Docker)             <-->  Azure Cache for Redis (SSL)   |
|  LocalStack S3 (Docker)       <-->  Azure Blob Storage            |
|  No service discovery         <-->  Eureka Server (:8761)         |
|  Prometheus + Grafana          <-->  Azure Monitor (future)        |
|                                                                   |
+------------------------------------------------------------------+
|                                                                   |
|  Profile activation:                                              |
|    SPRING_PROFILES_ACTIVE=azure                                   |
|                                                                   |
|  Each module has:                                                 |
|    application.yml          (default, local/docker/AWS)           |
|    application-azure.yml    (Azure-specific overrides)            |
|                                                                   |
|  Azure Event Hubs Kafka Config:                                   |
|    security.protocol: SASL_SSL                                    |
|    sasl.mechanism: PLAIN                                          |
|    sasl.jaas.config: $$ConnectionString + connection string       |
|                                                                   |
|  Azure Redis:                                                     |
|    Port: 6380 (SSL)                                               |
|    ssl.enabled: true                                              |
|                                                                   |
|  Azure PostgreSQL:                                                |
|    ?sslmode=require on JDBC URL                                   |
|                                                                   |
|  Estimated Azure cost: ~$150-200/month                            |
|    - Azure DB for PostgreSQL Flexible: ~$50/mo (B1ms)             |
|    - Azure Event Hubs Standard: ~$25/mo                           |
|    - Azure Cache for Redis Basic: ~$16/mo                         |
|    - Azure Blob Storage: ~$5/mo                                   |
|    - Azure Container Instances (9 services): ~$80/mo              |
+------------------------------------------------------------------+
```

---

## 19. Scheduled Tasks & Background Processing

```
+------------------------------------------------------------------+
|                    Scheduled Task Architecture                    |
+------------------------------------------------------------------+
|                                                                   |
|  job-monitoring-service (:8082)                                   |
|  ┌─────────────────────────────────────────────────────────────┐  |
|  │                                                             │  |
|  │  SlaEvaluationScheduler                                     │  |
|  │    @Scheduled(cron = "0 */5 * * * *")                       │  |
|  │    ─ Queries active jobs with SLA configured                │  |
|  │    ─ Checks latest execution durationMs vs slaSeconds       │  |
|  │    ─ Publishes SLA_VIOLATED JobEvent to Kafka               │  |
|  │                                                             │  |
|  │  HeartbeatMonitorScheduler                                  │  |
|  │    @Scheduled(fixedDelay = 60000ms)                         │  |
|  │    ─ Checks active SCHEDULED jobs for missing heartbeats    │  |
|  │    ─ Grace period: expectedRuntime + gracePeriod            │  |
|  │    ─ Publishes HEARTBEAT_MISSED JobEvent to Kafka           │  |
|  │                                                             │  |
|  │  ExponentialBackoffRetryExecutor                            │  |
|  │    ─ Implements RetryExecutor<T> functional interface       │  |
|  │    ─ delay = BASE_DELAY * backoffMultiplier^(attempt-1)     │  |
|  │    ─ Configurable via platform.job-monitor.retryBackoff     │  |
|  │                                                             │  |
|  │  JobRetryService + JobRetryController                       │  |
|  │    ─ POST /api/v1/jobs/{jobId}/retry                        │  |
|  │    ─ Validates job is ACTIVE, publishes RETRYING event      │  |
|  │                                                             │  |
|  └─────────────────────────────────────────────────────────────┘  |
|                                                                   |
|  alerting-service (:8083)                                         |
|  ┌─────────────────────────────────────────────────────────────┐  |
|  │                                                             │  |
|  │  AlertEventListener (Kafka Consumer)                        │  |
|  │    @KafkaListener(topics = "job-events")                    │  |
|  │    ─ Filters: FAILED, SLA_VIOLATED, HEARTBEAT_MISSED,      │  |
|  │              COMPLETED                                      │  |
|  │    ─ Builds evaluation context (failureCount, durationMs)   │  |
|  │    ─ Delegates to AlertService.evaluateRules()              │  |
|  │    ─ ErrorHandlingDeserializer + MANUAL_IMMEDIATE ack       │  |
|  │                                                             │  |
|  └─────────────────────────────────────────────────────────────┘  |
|                                                                   |
|  notification-service (:8084)                                     |
|  ┌─────────────────────────────────────────────────────────────┐  |
|  │                                                             │  |
|  │  ChannelDispatcherConfig — 5 real channels:                 │  |
|  │    ─ EMAIL:   JavaMailSender (SMTP / AWS SES)               │  |
|  │    ─ SLACK:   RestTemplate → webhook URL (JSON payload)     │  |
|  │    ─ SMS:     AWS SNS SDK (SnsClient + PublishRequest)      │  |
|  │    ─ PUSH:    FCM HTTP Legacy API (RestTemplate + auth)     │  |
|  │    ─ WEBHOOK: RestTemplate → recipient URL (generic HTTP)   │  |
|  │                                                             │  |
|  │  Injected as Map<String, NotificationDispatcher> bean       │  |
|  │                                                             │  |
|  └─────────────────────────────────────────────────────────────┘  |
|                                                                   |
|  job-queue-service (:8085)                                        |
|  ┌─────────────────────────────────────────────────────────────┐  |
|  │                                                             │  |
|  │  QueueTimeoutCleanupScheduler                               │  |
|  │    @Scheduled(fixedDelay = 5000ms)                          │  |
|  │    ─ Finds PROCESSING items with expired lockedUntil        │  |
|  │    ─ Releases to PENDING or moves to DEAD_LETTER            │  |
|  │                                                             │  |
|  │  QueueStatsService + GET /api/v1/queue/stats                │  |
|  │    ─ Returns counts per status: pending, processing,        │  |
|  │      completed, failed, deadLetter, cancelled               │  |
|  │    ─ Includes processingRate metric                         │  |
|  │                                                             │  |
|  └─────────────────────────────────────────────────────────────┘  |
|                                                                   |
+------------------------------------------------------------------+
```
