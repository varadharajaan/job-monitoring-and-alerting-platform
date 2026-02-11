# ADR-004: JWT Authentication with Multi-Tenancy Isolation

**Date:** 2026-01-17  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

The platform serves multiple tenants (organizations). Each tenant's jobs, alerts, and notifications must be isolated. Options:
1. **Session-based auth** — server-side sessions with cookies
2. **OAuth2 / OIDC** — delegated auth via external provider
3. **JWT + API Keys** — stateless tokens with self-contained claims
4. **Database-per-tenant** — physical isolation

## Decision

**JWT bearer tokens** for API authentication with **`X-Tenant-Id` header** for logical multi-tenancy within a shared database.

## Rationale

- **Stateless:** JWTs validated locally (no session store hit) — scales horizontally without sticky sessions
- **Tenant in token:** JWT payload includes `tenantId` claim, validated against `X-Tenant-Id` header
- **`TenantContext`** (ThreadLocal): Automatically populated by `JwtAuthenticationFilter`, available to all repository queries
- **Shared DB:** Single PostgreSQL instance with `tenant_id` column on every table — simpler ops than database-per-tenant
- **API Keys:** For machine-to-machine integrations, API keys map to a tenant + role
- **Trade-off:** JWT revocation requires Redis blocklist (implemented); token size larger than session IDs

## Consequences

- `JwtTokenProvider` generates/validates tokens with configurable expiry and secret
- `JwtAuthenticationFilter` extracts token from `Authorization: Bearer <token>` header
- `TenantContext` (ThreadLocal) propagated to Kafka event headers for cross-service tenant isolation
- Every JPA entity extends `BaseEntity` with `tenantId` field
- Repository queries filter by `tenantId` — no cross-tenant data leakage
- `SecurityConfig` defines public paths (health, auth endpoints) vs authenticated paths
