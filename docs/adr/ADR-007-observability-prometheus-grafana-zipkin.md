# ADR-007: Observability Stack — Prometheus, Grafana, Zipkin

**Date:** 2026-01-20  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

12 services plus 2 workers require production-grade observability: metrics, dashboards, distributed tracing, and alerting. Options:
1. **Commercial:** Datadog, New Relic, Dynatrace
2. **Cloud-native:** AWS CloudWatch + X-Ray / Azure Monitor + App Insights
3. **Open-source:** Prometheus + Grafana + Zipkin/Jaeger

## Decision

**Prometheus** (metrics) + **Grafana** (dashboards) + **Zipkin** (distributed tracing) for local/Docker environments. Cloud environments additionally use native monitoring (CloudWatch, Azure Monitor).

## Rationale

- **Prometheus:** De facto standard for K8s metrics; Spring Boot Actuator exposes `/actuator/prometheus` natively via Micrometer
- **Grafana:** Rich dashboards, alerting, supports Prometheus data source; community dashboards for Spring Boot, JVM, Kafka, HikariCP
- **Zipkin:** Lightweight, Spring Boot auto-configuration (`spring-boot-starter-actuator` + `micrometer-tracing-bridge-brave`); trace correlation across Kafka consumers
- **Cloud augmentation:** Azure deployments add Application Insights (Terraform module `monitoring`); AWS deployments use CloudWatch for infrastructure metrics
- **Trade-off:** Self-hosted observability requires operational effort; in production, cloud-native tools supplement

## Consequences

- `deploy/docker/prometheus.yml` scrapes all 14 service endpoints every 15s
- `deploy/docker/alertmanager.yml` + `alertmanager-rules.yml` for alert routing
- `deploy/docker/grafana/` contains provisioned data sources and dashboards
- Every service exposes: `health`, `info`, `prometheus`, `metrics` actuator endpoints
- Micrometer tags: `application`, `method`, `uri`, `status` for consistent metric labeling
- Custom metrics: `PlatformMetrics` class provides `recordJobDuration()`, `incrementAlertCount()`, etc.
