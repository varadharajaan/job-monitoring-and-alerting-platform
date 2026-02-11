# ADR-005: Spring Cloud Config Server + Eureka Service Discovery

**Date:** 2026-01-17  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

12 services need externalized configuration and service discovery. Options:
1. **Config:** Environment variables only vs. Spring Cloud Config Server vs. Consul KV
2. **Discovery:** Kubernetes DNS only vs. Eureka vs. Consul vs. client-side load balancing

## Decision

- **Spring Cloud Config Server** for centralized configuration
- **Eureka** for service discovery and registration

## Rationale

- **Config Server:** Single source of truth for all service configs; supports profiles (`local`, `aws`, `azure`, `prod`); Git backend for versioned config; native mode for local development
- **Eureka:** Battle-tested in Netflix OSS; self-preservation mode prevents cascading failures; health-check integration with Spring Boot Actuator; works behind corporate firewalls (HTTP-based, no multicast)
- **Why not K8s-native only:** Platform must work in Docker Compose (local dev), EKS (AWS), and AKS (Azure). Eureka provides a consistent discovery mechanism across all environments
- **Trade-off:** Extra infrastructure (2 services), but they're lightweight and provide consistent behavior across dev/cloud

## Consequences

- Config Server runs on port 8888 with `native` profile (classpath configs) or `git` profile
- All services import config: `spring.config.import: optional:configserver:${CONFIG_SERVER_URL}`
- Eureka Server runs on port 8761 with HTTP Basic auth
- Services register with Eureka and use `@LoadBalanced RestTemplate` for inter-service calls
- Health check URL: `/actuator/health` wired into Eureka instance metadata
