# ADR-003: TimescaleDB over Plain PostgreSQL

**Date:** 2026-01-16  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

The job monitoring platform generates time-series data: job executions with timestamps, heartbeats, SLA metrics, and performance samples. Options:
1. **Plain PostgreSQL** — standard relational database
2. **TimescaleDB** — PostgreSQL extension optimized for time-series
3. **InfluxDB** — purpose-built time-series database
4. **ClickHouse** — columnar analytics database

## Decision

**TimescaleDB** (PostgreSQL 16 extension) for all services.

## Rationale

- **Still PostgreSQL:** Full SQL, ACID transactions, JPA/Hibernate support, Flyway migrations — no new tooling
- **Hypertables:** `job_executions` table auto-partitioned by time chunks — queries like "last 24h of executions" scan only relevant chunks
- **Compression:** 90%+ compression on historical data with automatic policies
- **Continuous aggregates:** Real-time materialized views for dashboard metrics (avg duration, failure rates)
- **Cloud deployment:** On AWS/Azure, we use standard PostgreSQL Flexible Server (TimescaleDB extension enabled via `shared_preload_libraries`)
- **Trade-off:** Adds ~10MB to Docker image; hypertable creation requires Flyway migration awareness

## Consequences

- Docker Compose uses `timescale/timescaledb:latest-pg16` image
- Flyway migrations create hypertables: `SELECT create_hypertable('job_executions', 'started_at')`
- `pg_stat_statements` extension enabled for DB performance monitoring service (#32)
- Testcontainers use the same TimescaleDB image for test parity
