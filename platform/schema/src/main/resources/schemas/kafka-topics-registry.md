# Kafka Topics Registry

> Canonical list of all Kafka topics used in the Job Monitoring & Alerting Platform.

## Topic Definitions

| Topic Name | Producer | Consumer(s) | Key | Value Schema | Partitions | Retention |
|---|---|---|---|---|---|---|
| `job-events` | job-monitoring-service | alerting-service, job-worker | `jobId` (UUID) | [job-event.json](events/job-event.json) | 12 | 7 days |
| `alert-events` | alerting-service | notification-service | `alertId` (UUID) | [alert-event.json](events/alert-event.json) | 6 | 7 days |
| `notification-events` | notification-service | notification-worker | `notificationId` (UUID) | [notification-event.json](events/notification-event.json) | 6 | 3 days |
| `queue-events` | job-queue-service | job-worker | `queuedJobId` (UUID) | [queue-event.json](events/queue-event.json) | 12 | 7 days |
| `log-events` | log-ingestion-service | job-monitoring-service | `jobId` (UUID) | Raw log JSON | 12 | 3 days |
| `dead-letter` | Any (on failure) | Manual review | Original key | Original payload + error metadata | 3 | 30 days |

## Consumer Groups

| Group ID | Service | Topics Consumed |
|---|---|---|
| `alerting-group` | alerting-service | `job-events` |
| `notification-group` | notification-service | `alert-events` |
| `job-worker-group` | job-worker | `job-events`, `queue-events` |
| `notification-worker-group` | notification-worker | `notification-events` |
| `log-processor-group` | job-monitoring-service | `log-events` |

## Conventions

- **Key format:** UUID string — ensures consistent partitioning for all events related to the same entity
- **Value format:** JSON, validated against JSON Schema definitions in `schemas/events/`
- **Headers:** All events include `X-Tenant-Id` header for multi-tenant routing
- **Serialization:** `StringSerializer` for keys, `JsonSerializer` (Jackson) for values
- **Deserialization:** `StringDeserializer` for keys, `JsonDeserializer` with trusted packages `com.jobmonitor.*`
- **Idempotency:** Consumers must be idempotent — at-least-once delivery semantics
- **Error handling:** Failed messages routed to `dead-letter` topic after 3 retry attempts
