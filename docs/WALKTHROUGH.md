# Walkthrough & Execution Guide

> **Job Monitoring & Alerting Platform** — Complete Setup, Local Dev, AWS & Azure Deployment
> Version 1.0.0 | Last updated: 2026-02-10

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Local Development Setup (Step by Step)](#3-local-development-setup)
4. [Running Tests](#4-running-tests)
5. [Docker Compose (Full Stack Local)](#5-docker-compose-full-stack-local)
6. [AWS Deployment](#6-aws-deployment)
7. [Azure Deployment](#7-azure-deployment)
8. [Kubernetes Deployment (Generic)](#8-kubernetes-deployment)
9. [Monitoring & Observability](#9-monitoring--observability)
10. [API Quick Reference](#10-api-quick-reference)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENTS                                     │
│              (Web UI / CLI / External Schedulers / APIs)             │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ HTTPS (REST/JSON)
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    INGESTION GATEWAY (:8080)                         │
│              Rate Limiting (Bucket4j) + Request Routing              │
└──┬──────┬──────┬──────┬──────┬──────────────────────────────────────┘
   │      │      │      │      │
   ▼      ▼      ▼      ▼      ▼
┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐  ┌──────────┐  ┌──────────┐
│ AUTH ││ JOB  ││ALERT ││NOTIF ││ JOB  │  │  EUREKA  │  │  CONFIG  │
│:8081 ││MONIT ││:8083 ││:8084 ││QUEUE │  │  SERVER  │  │  SERVER  │
│      ││:8082 ││      ││      ││:8085 │  │  :8761   │  │  :8888   │
└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘  └──────────┘  └──────────┘
   │       │       │       │       │
   └───────┴───────┴───────┴───────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  KAFKA EVENT BUS (KRaft 3.7.0)                       │
│  job-events │ alert-events │ notification-events │ queue-events      │
└──────────┬──────────────────────────────┬───────────────────────────┘
           │                              │
           ▼                              ▼
    ┌─────────────┐              ┌──────────────────┐
    │  JOB WORKER │              │ NOTIFICATION     │
    │  (headless) │              │ WORKER (headless) │
    └─────────────┘              └──────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     DATA & INFRASTRUCTURE                            │
│  PostgreSQL/TimescaleDB │ Redis │ S3/Azure Blob │ SMTP/SES          │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     OBSERVABILITY                                     │
│  Prometheus │ Grafana │ Zipkin │ Alertmanager │ Micrometer            │
└─────────────────────────────────────────────────────────────────────┘
```

### Service Ports

| Service | Port | Description |
|---------|------|-------------|
| Ingestion Gateway | 8080 | API entry point with rate limiting |
| Auth Service | 8081 | JWT authentication, API key management |
| Job Monitoring | 8082 | Job registration, execution tracking, SLA |
| Alerting Service | 8083 | Alert rule engine, Kafka consumer |
| Notification Service | 8084 | Multi-channel notification dispatch |
| Job Queue Service | 8085 | Priority queue, distributed claiming |
| Eureka Server | 8761 | Service discovery |
| Config Server | 8888 | Centralized configuration |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3000 | Dashboards |
| Zipkin | 9411 | Distributed tracing |
| Alertmanager | 9093 | Alert routing |
| Kafka | 9092 | Event bus |
| PostgreSQL | 5432 | Primary database |
| Redis | 6379 | Caching & rate limiting |
| MailHog | 8025 | Local SMTP testing (Web UI) |

---

## 2. Prerequisites

### Required Software

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | 17+ | Application runtime |
| **Gradle** | 8.7+ (or use `./gradlew`) | Build tool |
| **Docker** | 24+ | Container runtime |
| **Docker Compose** | v2+ | Local infrastructure |
| **Git** | 2.40+ | Version control |

### Optional (for cloud deployment)

| Tool | Purpose |
|------|---------|
| **AWS CLI** | AWS deployment |
| **Azure CLI** | Azure deployment |
| **kubectl** | Kubernetes operations |
| **Helm** | K8s package management |
| **Terraform** | Infrastructure as Code |

---

## 3. Local Development Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/varadharajaan/job-monitoring-and-alerting-platform.git
cd job-monitoring-and-alerting-platform
git checkout feature/platform-foundation
```

### Step 2: Configure Environment Variables

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
# ── Database ──
POSTGRES_DB=jobmonitor
POSTGRES_USER=jobmonitor
POSTGRES_PASSWORD=changeme
DB_URL=jdbc:postgresql://localhost:5432/jobmonitor
DB_USERNAME=jobmonitor
DB_PASSWORD=changeme

# ── Redis ──
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=changeme

# ── Kafka ──
KAFKA_BOOTSTRAP=localhost:9092

# ── JWT ──
JWT_SECRET=your-256-bit-secret-key-must-be-at-least-32-chars-long-for-hs256!!!

# ── SMTP (MailHog for local) ──
SMTP_HOST=localhost
SMTP_PORT=1025

# ── Grafana ──
GRAFANA_ADMIN_PASSWORD=admin

# ── Eureka ──
EUREKA_USER=eureka
EUREKA_PASSWORD=eureka
EUREKA_ENABLED=false

# ── Config Server ──
CONFIG_USER=config
CONFIG_PASSWORD=config
```

### Step 3: Start Infrastructure (Docker Compose)

```bash
cd deploy/docker
docker compose up -d postgres kafka redis localstack prometheus grafana zipkin alertmanager mailhog
```

Wait for all services to be healthy:

```bash
docker compose ps
```

Expected output — all containers should show `healthy` or `running`.

### Step 4: Build the Application

```bash
# From project root
./gradlew clean build -x test
```

> **Note**: First build downloads dependencies (~5 min). Subsequent builds are faster.

### Step 5: Run Flyway Migrations

Migrations run automatically when any JPA-enabled service starts. The first service to start will create all tables.

### Step 6: Start Services (Order Matters)

Start services in this order:

```bash
# Terminal 1 — Eureka Server (start first for service discovery)
./gradlew :services:eureka-server:bootRun

# Terminal 2 — Config Server
./gradlew :services:config-server:bootRun

# Terminal 3 — Auth Service
./gradlew :services:auth-service:bootRun

# Terminal 4 — Job Monitoring Service
./gradlew :services:job-monitoring-service:bootRun

# Terminal 5 — Alerting Service
./gradlew :services:alerting-service:bootRun

# Terminal 6 — Notification Service
./gradlew :services:notification-service:bootRun

# Terminal 7 — Job Queue Service
./gradlew :services:job-queue-service:bootRun

# Terminal 8 — Ingestion Gateway
./gradlew :services:ingestion-gateway:bootRun

# Terminal 9 — Job Worker
./gradlew :workers:job-worker:bootRun

# Terminal 10 — Notification Worker
./gradlew :workers:notification-worker:bootRun
```

### Step 7: Verify Services are Running

```bash
# Health checks
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Auth
curl http://localhost:8082/actuator/health   # Job Monitor
curl http://localhost:8083/actuator/health   # Alerting
curl http://localhost:8084/actuator/health   # Notification
curl http://localhost:8085/actuator/health   # Job Queue
curl http://localhost:8761/actuator/health   # Eureka
curl http://localhost:8888/actuator/health   # Config
```

### Step 8: Test the Happy Path

```bash
# 1. Register a user
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "SecurePass123!",
    "email": "admin@test.com",
    "fullName": "Admin User",
    "tenantId": "tenant-001"
  }'

# 2. Login to get JWT token
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"SecurePass123!"}' | jq -r '.data.accessToken')

# 3. Ingest a job event
curl -X POST http://localhost:8080/api/v1/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: tenant-001" \
  -H "Content-Type: application/json" \
  -d '{
    "jobName": "data-pipeline-daily",
    "eventType": "STARTED",
    "cronExpression": "0 0 2 * * *",
    "metadata": {"source": "airflow"}
  }'

# 4. Register a job for monitoring
curl -X POST http://localhost:8082/api/v1/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: tenant-001" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "data-pipeline-daily",
    "cronExpression": "0 0 2 * * *",
    "expectedDurationMs": 3600000,
    "slaDeadlineMinutes": 120
  }'

# 5. Create an alert rule
curl -X POST http://localhost:8083/api/v1/alerts/rules \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: tenant-001" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Pipeline Failure Alert",
    "conditionType": "FAILURE_COUNT",
    "operator": "GREATER_THAN",
    "threshold": 2,
    "severity": "HIGH",
    "notificationChannels": ["EMAIL", "SLACK"]
  }'

# 6. Enqueue a background job
curl -X POST http://localhost:8085/api/v1/queue/enqueue \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: tenant-001" \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "DATA_EXPORT",
    "priority": 5,
    "payload": {"format": "csv", "target": "s3://exports/"}
  }'

# 7. Check queue stats
curl http://localhost:8085/api/v1/queue/stats \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: tenant-001"
```

---

## 4. Running Tests

### Run All Tests

```bash
./gradlew clean test
```

### Run Tests for a Specific Module

```bash
./gradlew :services:auth-service:test
./gradlew :services:job-queue-service:test
./gradlew :services:ingestion-gateway:test
./gradlew :workers:job-worker:test
./gradlew :workers:notification-worker:test
```

### Test Report

After running tests, HTML reports are at:
```
<module>/build/reports/tests/test/index.html
```

### Test Architecture

| Test Type | Framework | Infrastructure |
|-----------|-----------|----------------|
| Unit Tests | JUnit 5 + Mockito | None (mocked) |
| Controller Tests | `@WebMvcTest` + MockMvc | Spring slice, mocked beans |
| Integration Tests | `AbstractIntegrationTest` | Testcontainers (Postgres, Kafka, Redis) |

---

## 5. Docker Compose (Full Stack Local)

### Build All Docker Images

```bash
# Build all service JARs
./gradlew clean bootJar

# Build Docker images
docker compose -f deploy/docker/docker-compose.yml build
```

### Start Everything

```bash
cd deploy/docker
docker compose --profile local up -d
```

### Stop Everything

```bash
docker compose down
docker compose down -v   # Also remove volumes (data reset)
```

### Access UIs

| UI | URL | Credentials |
|----|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | eureka / eureka |
| Grafana | http://localhost:3000 | admin / (from .env) |
| Prometheus | http://localhost:9090 | — |
| Zipkin | http://localhost:9411 | — |
| Alertmanager | http://localhost:9093 | — |
| MailHog (Email) | http://localhost:8025 | — |
| Swagger UI (per service) | http://localhost:{port}/swagger-ui.html | — |

---

## 6. AWS Deployment

### Architecture on AWS

```
┌─────────────────────────────────────────────────────────────────────┐
│                         AWS CLOUD                                    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    VPC (10.0.0.0/16)                          │   │
│  │                                                                │   │
│  │  ┌─────────────────────────┐  ┌────────────────────────────┐  │   │
│  │  │   Public Subnets         │  │   Private Subnets          │  │   │
│  │  │                          │  │                             │  │   │
│  │  │  ┌────────────────┐     │  │  ┌──────────────────────┐  │  │   │
│  │  │  │  ALB (App       │     │  │  │  ECS/EKS Cluster     │  │  │   │
│  │  │  │  Load Balancer) │─────┼──┼─▶│  9 services + 2      │  │  │   │
│  │  │  └────────────────┘     │  │  │  workers (Fargate)    │  │  │   │
│  │  │                          │  │  └──────────────────────┘  │  │   │
│  │  │  ┌────────────────┐     │  │                             │  │   │
│  │  │  │  NAT Gateway    │     │  │  ┌──────────────────────┐  │  │   │
│  │  │  └────────────────┘     │  │  │  Amazon RDS            │  │  │   │
│  │  │                          │  │  │  (PostgreSQL 16)       │  │  │   │
│  │  └─────────────────────────┘  │  └──────────────────────┘  │  │   │
│  │                                │                             │  │   │
│  │                                │  ┌──────────────────────┐  │  │   │
│  │                                │  │  Amazon ElastiCache   │  │  │   │
│  │                                │  │  (Redis 7)            │  │  │   │
│  │                                │  └──────────────────────┘  │  │   │
│  │                                │                             │  │   │
│  │                                │  ┌──────────────────────┐  │  │   │
│  │                                │  │  Amazon MSK           │  │  │   │
│  │                                │  │  (Kafka 3.7)          │  │  │   │
│  │                                │  └──────────────────────┘  │  │   │
│  │                                └────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │
│  │ Amazon SES │ │ Amazon SNS │ │ Amazon S3  │ │ CloudWatch │       │
│  │ (Email)    │ │ (SMS/Push) │ │ (Logs/Blob)│ │ (Metrics)  │       │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

### Step-by-Step AWS Deployment

#### Step 1: Create Infrastructure

```bash
# Create VPC, subnets, security groups
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --tag-specifications 'ResourceType=vpc,Tags=[{Key=Name,Value=jobmonitor-vpc}]'

# Create RDS PostgreSQL
aws rds create-db-instance \
  --db-instance-identifier jobmonitor-db \
  --db-instance-class db.t3.medium \
  --engine postgres \
  --engine-version 16.1 \
  --master-username jobmonitor \
  --master-user-password '<STRONG_PASSWORD>' \
  --allocated-storage 50 \
  --vpc-security-group-ids sg-xxxxx

# Create ElastiCache Redis
aws elasticache create-replication-group \
  --replication-group-id jobmonitor-redis \
  --replication-group-description "Job Monitor Redis" \
  --engine redis \
  --cache-node-type cache.t3.medium \
  --num-cache-clusters 2

# Create MSK Kafka cluster
aws kafka create-cluster \
  --cluster-name jobmonitor-kafka \
  --kafka-version 3.7.0 \
  --number-of-broker-nodes 3 \
  --broker-node-group-info '{
    "InstanceType": "kafka.t3.small",
    "ClientSubnets": ["subnet-xxx", "subnet-yyy"],
    "SecurityGroups": ["sg-xxx"]
  }'
```

#### Step 2: Create ECR Repositories & Push Images

```bash
# Create repos for each service
for svc in eureka-server config-server auth-service ingestion-gateway \
           job-monitoring-service alerting-service notification-service \
           job-queue-service job-worker notification-worker; do
  aws ecr create-repository --repository-name jobmonitor/$svc
done

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com

# Build and push each service
./gradlew clean bootJar
for svc in eureka-server config-server auth-service ingestion-gateway \
           job-monitoring-service alerting-service notification-service \
           job-queue-service job-worker notification-worker; do
  docker build -t jobmonitor/$svc services/$svc/ 2>/dev/null || docker build -t jobmonitor/$svc workers/$svc/
  docker tag jobmonitor/$svc:latest <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/jobmonitor/$svc:latest
  docker push <ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/jobmonitor/$svc:latest
done
```

#### Step 3: Deploy to ECS/Fargate

```bash
# Create ECS cluster
aws ecs create-cluster --cluster-name jobmonitor-cluster --capacity-providers FARGATE

# Create task definitions (one per service)
# Example for ingestion-gateway:
aws ecs register-task-definition --cli-input-json '{
  "family": "ingestion-gateway",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [{
    "name": "ingestion-gateway",
    "image": "<ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com/jobmonitor/ingestion-gateway:latest",
    "portMappings": [{"containerPort": 8080}],
    "environment": [
      {"name": "DB_URL", "value": "jdbc:postgresql://<RDS_ENDPOINT>:5432/jobmonitor"},
      {"name": "KAFKA_BOOTSTRAP", "value": "<MSK_BOOTSTRAP>"},
      {"name": "REDIS_HOST", "value": "<ELASTICACHE_ENDPOINT>"},
      {"name": "EUREKA_ENABLED", "value": "true"},
      {"name": "EUREKA_URL", "value": "http://eureka:eureka@eureka-server.local:8761/eureka/"},
      {"name": "ZIPKIN_URL", "value": "http://zipkin.local:9411/api/v2/spans"}
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/ecs/jobmonitor",
        "awslogs-region": "us-east-1",
        "awslogs-stream-prefix": "ingestion-gateway"
      }
    }
  }]
}'

# Create services with ALB
aws ecs create-service \
  --cluster jobmonitor-cluster \
  --service-name ingestion-gateway \
  --task-definition ingestion-gateway \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration '{
    "awsvpcConfiguration": {
      "subnets": ["subnet-xxx"],
      "securityGroups": ["sg-xxx"],
      "assignPublicIp": "DISABLED"
    }
  }' \
  --load-balancers '[{
    "targetGroupArn": "arn:aws:elasticloadbalancing:...",
    "containerName": "ingestion-gateway",
    "containerPort": 8080
  }]'
```

#### Step 4: Configure AWS Services

```bash
# Set up SES for email
aws ses verify-email-identity --email-address noreply@yourdomain.com

# Set up SNS for SMS
aws sns set-sms-attributes --attributes '{"DefaultSMSType": "Transactional"}'

# Create S3 bucket for logs
aws s3 mb s3://jobmonitor-logs-<ACCOUNT_ID>
```

---

## 7. Azure Deployment

### Architecture on Azure

```
┌─────────────────────────────────────────────────────────────────────┐
│                        AZURE CLOUD                                   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  Resource Group: rg-jobmonitor                │   │
│  │                                                                │   │
│  │  ┌────────────────────────────────────────────────────────┐   │   │
│  │  │              AKS Cluster (3 nodes)                      │   │   │
│  │  │                                                          │   │   │
│  │  │  ┌─────────────────┐  ┌──────────────────────────────┐ │   │   │
│  │  │  │ nginx Ingress    │  │  Service Pods                 │ │   │   │
│  │  │  │ Controller       │──│  9 services + 2 workers       │ │   │   │
│  │  │  └─────────────────┘  │  (managed by K8s manifests)    │ │   │   │
│  │  │                        └──────────────────────────────┘ │   │   │
│  │  └────────────────────────────────────────────────────────┘   │   │
│  │                                                                │   │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐    │   │
│  │  │ Azure Database │ │ Azure Cache    │ │ Azure Event    │    │   │
│  │  │ for PostgreSQL │ │ for Redis      │ │ Hubs (Kafka)   │    │   │
│  │  │ Flexible Server│ │ Premium        │ │ Standard       │    │   │
│  │  └────────────────┘ └────────────────┘ └────────────────┘    │   │
│  │                                                                │   │
│  │  ┌────────────────┐ ┌────────────────┐ ┌────────────────┐    │   │
│  │  │ Azure Blob     │ │ Azure Monitor  │ │ Azure          │    │   │
│  │  │ Storage        │ │ + App Insights │ │ Container      │    │   │
│  │  │ (Logs/Files)   │ │ (Metrics/Trace)│ │ Registry (ACR) │    │   │
│  │  └────────────────┘ └────────────────┘ └────────────────┘    │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Step-by-Step Azure Deployment

#### Step 1: Create Resource Group & Infrastructure

```bash
# Login
az login

# Create resource group
az group create --name rg-jobmonitor --location eastus

# Create Azure Database for PostgreSQL
az postgres flexible-server create \
  --resource-group rg-jobmonitor \
  --name jobmonitor-pg \
  --location eastus \
  --admin-user jobmonitor \
  --admin-password '<STRONG_PASSWORD>' \
  --sku-name Standard_B2ms \
  --storage-size 64 \
  --version 16

# Create database
az postgres flexible-server db create \
  --resource-group rg-jobmonitor \
  --server-name jobmonitor-pg \
  --database-name jobmonitor

# Create Azure Cache for Redis
az redis create \
  --resource-group rg-jobmonitor \
  --name jobmonitor-redis \
  --location eastus \
  --sku Premium \
  --vm-size P1

# Create Event Hubs namespace (Kafka-compatible)
az eventhubs namespace create \
  --resource-group rg-jobmonitor \
  --name jobmonitor-eventhubs \
  --location eastus \
  --sku Standard \
  --enable-kafka true

# Create Event Hubs (topics)
for topic in job-events alert-events notification-events queue-events dead-letter; do
  az eventhubs eventhub create \
    --resource-group rg-jobmonitor \
    --namespace-name jobmonitor-eventhubs \
    --name "job-monitor.$topic" \
    --partition-count 6
done

# Create Azure Blob Storage
az storage account create \
  --resource-group rg-jobmonitor \
  --name jobmonitorlogs \
  --location eastus \
  --sku Standard_LRS

# Create AKS cluster
az aks create \
  --resource-group rg-jobmonitor \
  --name jobmonitor-aks \
  --node-count 3 \
  --node-vm-size Standard_DS3_v2 \
  --enable-managed-identity \
  --generate-ssh-keys

# Get AKS credentials
az aks get-credentials --resource-group rg-jobmonitor --name jobmonitor-aks
```

#### Step 2: Create ACR & Push Images

```bash
# Create Azure Container Registry
az acr create --resource-group rg-jobmonitor --name jobmonitoracr --sku Standard

# Attach ACR to AKS
az aks update --resource-group rg-jobmonitor --name jobmonitor-aks --attach-acr jobmonitoracr

# Login to ACR
az acr login --name jobmonitoracr

# Build and push images
./gradlew clean bootJar
for svc in eureka-server config-server auth-service ingestion-gateway \
           job-monitoring-service alerting-service notification-service \
           job-queue-service job-worker notification-worker; do
  docker build -t jobmonitoracr.azurecr.io/jobmonitor/$svc:latest services/$svc/ 2>/dev/null || \
  docker build -t jobmonitoracr.azurecr.io/jobmonitor/$svc:latest workers/$svc/
  docker push jobmonitoracr.azurecr.io/jobmonitor/$svc:latest
done
```

#### Step 3: Deploy to AKS

```bash
# Apply K8s manifests
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secrets.yaml  # Update with real Azure connection strings first!
kubectl apply -f deploy/k8s/infra/
kubectl apply -f deploy/k8s/monitoring/
kubectl apply -f deploy/k8s/services/
kubectl apply -f deploy/k8s/hpa.yaml
kubectl apply -f deploy/k8s/ingress.yaml

# Verify deployments
kubectl get pods -n jobmonitor
kubectl get services -n jobmonitor
kubectl get hpa -n jobmonitor
```

#### Step 4: Configure Azure-Specific Settings

Update `deploy/k8s/configmap.yaml` with Azure connection strings:

```yaml
DB_URL: "jdbc:postgresql://jobmonitor-pg.postgres.database.azure.com:5432/jobmonitor?sslmode=require"
REDIS_HOST: "jobmonitor-redis.redis.cache.windows.net"
KAFKA_BOOTSTRAP: "jobmonitor-eventhubs.servicebus.windows.net:9093"
SPRING_PROFILES_ACTIVE: "azure"
```

---

## 8. Kubernetes Deployment

### Manifest Structure

```
deploy/k8s/
├── namespace.yaml          # jobmonitor namespace
├── configmap.yaml          # Shared configuration
├── secrets.yaml            # Sensitive values (base64)
├── ingress.yaml            # nginx ingress rules
├── hpa.yaml                # Horizontal Pod Autoscalers
├── infra/
│   ├── postgres.yaml       # PostgreSQL StatefulSet
│   ├── kafka.yaml          # Kafka StatefulSet
│   └── redis.yaml          # Redis Deployment
├── monitoring/
│   └── prometheus-grafana.yaml
└── services/
    ├── eureka-server.yaml
    ├── auth-service.yaml
    ├── ingestion-gateway.yaml
    ├── job-monitoring-service.yaml
    ├── alerting-service.yaml
    ├── notification-service.yaml
    ├── job-queue-service.yaml
    ├── job-worker.yaml
    └── notification-worker.yaml
```

### Apply All Manifests

```bash
# Create namespace first
kubectl apply -f deploy/k8s/namespace.yaml

# Apply in order
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secrets.yaml
kubectl apply -f deploy/k8s/infra/
kubectl apply -f deploy/k8s/monitoring/
kubectl apply -f deploy/k8s/services/
kubectl apply -f deploy/k8s/hpa.yaml
kubectl apply -f deploy/k8s/ingress.yaml

# Verify
kubectl get all -n jobmonitor
```

### Scaling

```bash
# Manual scaling
kubectl scale deployment ingestion-gateway --replicas=5 -n jobmonitor

# HPA handles auto-scaling based on CPU/memory thresholds
kubectl get hpa -n jobmonitor
```

---

## 9. Monitoring & Observability

### Prometheus Metrics

All services expose metrics at `/actuator/prometheus`. Custom business metrics include:

| Metric | Service | Description |
|--------|---------|-------------|
| `gateway_ingest_seconds` | Ingestion Gateway | Ingestion latency |
| `auth_login_seconds` | Auth Service | Login latency |
| `job_create_seconds` | Job Monitoring | Job creation time |
| `alert_rule_create_seconds` | Alerting | Rule creation time |
| `alert_evaluate_seconds` | Alerting | Rule evaluation time |
| `notification_send_seconds` | Notification | Notification dispatch time |
| `queue_enqueue_seconds` | Job Queue | Enqueue latency |

### Grafana Dashboard

Pre-provisioned dashboard at `http://localhost:3000` includes:
- **Service Health Overview** — Up/down status for all services
- **Request Rate** — Per-service HTTP request rate
- **Error Rate** — 5xx error rate per service
- **P95 Response Time** — 95th percentile latency
- **JVM Heap Memory** — Heap usage per service
- **Business Metrics** — Ingestion rate, alert evaluation rate, notification rate, queue rate
- **Kafka Consumer Lag** — Per-consumer-group lag
- **GC Pause Time** — Garbage collection overhead
- **DB Connection Pool** — HikariCP active/idle connections

### Zipkin Tracing

Access at `http://localhost:9411`. All inter-service calls are traced with B3 propagation headers.

### Alert Rules

Prometheus Alertmanager fires on:
- **ServiceDown** — Any service down for >1 minute (critical)
- **HighErrorRate** — >5% 5xx rate for 2 minutes (warning)
- **HighMemoryUsage** — >85% heap usage for 5 minutes (warning)
- **HighGCPause** — GC pause >500ms/s for 3 minutes (warning)
- **QueueBacklog** — >100 pending items for 5 minutes (warning)
- **SlowIngestion** — P95 ingestion >2s for 3 minutes (warning)

---

## 10. API Quick Reference

### Auth Service (:8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login (returns JWT) |
| POST | `/api/v1/auth/refresh` | Refresh token |
| POST | `/api/v1/auth/api-keys` | Create API key |
| GET | `/api/v1/auth/api-keys` | List API keys |
| DELETE | `/api/v1/auth/api-keys/{id}` | Revoke API key |

### Ingestion Gateway (:8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ingest` | Ingest job event |

### Job Monitoring (:8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/jobs` | Register job |
| GET | `/api/v1/jobs` | List jobs (paginated) |
| GET | `/api/v1/jobs/{id}` | Get job details |
| PUT | `/api/v1/jobs/{id}` | Update job |
| DELETE | `/api/v1/jobs/{id}` | Deactivate job |
| POST | `/api/v1/jobs/{id}/executions` | Record execution |
| POST | `/api/v1/heartbeat/{jobId}` | Send heartbeat |

### Alerting Service (:8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/alerts/rules` | Create alert rule |
| GET | `/api/v1/alerts/rules` | List rules |
| PUT | `/api/v1/alerts/rules/{id}` | Update rule |
| GET | `/api/v1/alerts/history` | Alert history |
| POST | `/api/v1/alerts/{id}/acknowledge` | Acknowledge alert |

### Notification Service (:8084)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/notifications/send` | Send notification |
| GET | `/api/v1/notifications` | List notifications |
| POST | `/api/v1/notifications/templates` | Create template |
| GET | `/api/v1/notifications/templates` | List templates |

### Job Queue Service (:8085)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/queue/enqueue` | Enqueue job |
| POST | `/api/v1/queue/claim` | Claim next job |
| POST | `/api/v1/queue/{id}/complete` | Complete job |
| POST | `/api/v1/queue/{id}/fail` | Fail job |
| POST | `/api/v1/queue/{id}/cancel` | Cancel job |
| GET | `/api/v1/queue/stats` | Queue statistics |

---

## 11. Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| `Connection refused` to DB | Ensure PostgreSQL container is running: `docker compose ps postgres` |
| Kafka consumer not receiving messages | Check Kafka is healthy: `docker compose logs kafka` |
| JWT token expired | Login again to get new token |
| `409 Duplicate Resource` | Resource with same name already exists |
| Service not found in Eureka | Set `EUREKA_ENABLED=true` |
| Flyway migration fails | Check DB credentials and ensure TimescaleDB extension is installed |
| Build fails on OneDrive path | Add `layout.buildDirectory = file("C:/tmp/jmap-build/${project.name}/build")` to `allprojects` in `build.gradle` |

### Log Locations

```bash
# Docker container logs
docker compose logs -f <service-name>

# Kubernetes logs
kubectl logs -f deployment/<service-name> -n jobmonitor

# Local dev — stdout + structured JSON to logback
```

### Health Check Endpoints

Every service exposes:
- `GET /actuator/health` — Health status
- `GET /actuator/info` — Build info
- `GET /actuator/prometheus` — Prometheus metrics
- `GET /actuator/metrics` — Micrometer metrics

---

> **Questions?** Check the [ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed system design or the service-specific Swagger UI at `http://localhost:{port}/swagger-ui.html`.
