# ADR-002: Event-Driven Architecture with Apache Kafka

**Date:** 2026-01-15  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

Services need to communicate asynchronously for job state transitions, alerts, and notifications. Options:
1. **Synchronous REST** — direct HTTP calls between services
2. **RabbitMQ** — traditional message broker with queues
3. **Apache Kafka** — distributed event streaming platform
4. **AWS SQS/SNS** — cloud-native messaging

## Decision

**Apache Kafka** (KRaft mode, no ZooKeeper) as the central event backbone.

## Rationale

- **Event sourcing natural fit:** Job lifecycle (CREATED → RUNNING → COMPLETED/FAILED) maps directly to an append-only log
- **Replay capability:** Consumers can reprocess events from any offset — critical for debugging production issues
- **High throughput:** 100K+ events/sec on modest hardware; our job platform may ingest thousands of job heartbeats per second
- **Consumer groups:** Multiple consumers (alerting, notification, analytics) independently consume the same event stream
- **Cloud portability:** Runs locally (KRaft), on AWS (MSK), and Azure (Event Hubs with Kafka API)
- **Trade-off:** More complex than RabbitMQ, eventual consistency model

## Consequences

- All inter-service communication goes through Kafka topics (`job-events`, `alert-events`, `notification-events`, `queue-events`, `log-events`)
- Services must be idempotent — at-least-once delivery means duplicate events are possible
- `platform:common` provides shared `KafkaConfig`, event DTOs, and producer/consumer templates
- Dead-letter topic (`dead-letter`) for unprocessable messages
- Azure profile uses SASL_SSL with `$ConnectionString` auth; AWS uses TLS with MSK IAM
