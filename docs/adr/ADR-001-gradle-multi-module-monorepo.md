# ADR-001: Gradle Multi-Module Monorepo

**Date:** 2026-01-15  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

We need a project structure that supports 12+ microservices sharing common libraries, event schemas, and build infrastructure. Options considered:
1. **Polyrepo** — each service in its own Git repository
2. **Monorepo with Maven** — single repo, Maven multi-module
3. **Monorepo with Gradle** — single repo, Gradle multi-module

## Decision

**Gradle multi-module monorepo** with hierarchical structure:
```
platform/       → shared libs (common, schema)
services/       → 10 microservices
workers/        → 2 async workers
```

## Rationale

- **Shared code** (DTOs, security, Kafka config, exceptions) changes propagate instantly — no version dance across repos
- **Gradle** over Maven: faster incremental builds (2-5x), Kotlin DSL for type-safe config, built-in parallel task execution
- **Monorepo** simplifies CI (single pipeline builds everything), atomic cross-service refactoring, consistent dependency versions
- **Trade-off:** larger checkout size, but our codebase is ~50K LOC — well within monorepo viability

## Consequences

- All services share the same Gradle wrapper version (8.7)
- `platform:common` is a `java-library` with `testFixtures` for shared test infrastructure
- `settings.gradle` is the single source of truth for module inclusion
- CI builds entire repo; deploy pipelines filter by changed paths
