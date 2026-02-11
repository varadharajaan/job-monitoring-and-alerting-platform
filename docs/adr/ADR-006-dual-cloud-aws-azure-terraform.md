# ADR-006: Dual-Cloud Deployment (AWS + Azure) with Terraform IaC

**Date:** 2026-02-11  
**Status:** Accepted  
**Deciders:** Platform Team

## Context

The platform must deploy to both AWS and Azure to support different customer requirements. Options:
1. **AWS-only** with CloudFormation
2. **Azure-only** with Bicep/ARM templates
3. **Dual-cloud** with Terraform (HCL)
4. **Pulumi** (TypeScript-based IaC)

## Decision

**Terraform** with separate root modules for AWS and Azure under `deploy/aws/terraform/` and `deploy/azure/terraform/`.

## Rationale

- **Terraform:** Single IaC language for both clouds; largest community; mature provider ecosystem
- **Separate modules (not abstracted):** AWS and Azure have fundamentally different services. Abstracting them into a "cloud-agnostic" layer adds complexity without value. Each cloud gets its own idiomatic Terraform.
- **Service mapping is explicit:**

| Need         | AWS                     | Azure                          |
|-------------|-------------------------|--------------------------------|
| Kubernetes  | EKS 1.29                | AKS 1.29                       |
| Database    | RDS PostgreSQL 16       | Flexible Server PG 16          |
| Cache       | ElastiCache Redis 7.1   | Azure Cache for Redis          |
| Messaging   | MSK Kafka 3.7 (TLS)    | Event Hubs (SASL_SSL, port 9093)|
| Search      | OpenSearch 2.11         | Cognitive Search               |
| Secrets     | Parameter Store / Secrets Manager | Key Vault             |

- **Spring profiles:** `application-aws.yml` and `application-azure.yml` per service with cloud-specific connection strings and auth
- **Trade-off:** Maintaining two parallel Terraform codebases, but the services themselves deploy via identical K8s manifests

## Consequences

- `deploy/aws/terraform/` — 10 modules (VPC, EKS, RDS, ElastiCache, MSK, OpenSearch, ECR, S3, Notifications, ALB)
- `deploy/azure/terraform/` — 10 modules (VNet, AKS, PostgreSQL, Redis, Event Hubs, Search, ACR, Storage, Monitoring, Key Vault)
- GitHub Actions: `deploy-aws.yml` (ECR → EKS), `deploy-azure.yml` (ACR → AKS), `infrastructure.yml` (Terraform plan/apply)
- K8s manifests in `deploy/k8s/` are cloud-agnostic — same YAML works on both EKS and AKS
- Container images tagged by git SHA; registries differ (ECR vs ACR)
