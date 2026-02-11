# ============================================================================
# AWS Infrastructure for Job Monitoring & Alerting Platform
# ============================================================================
# Usage:
#   cd deploy/aws/terraform
#   terraform init
#   terraform plan -var-file="prod.tfvars"
#   terraform apply -var-file="prod.tfvars"
# ============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.40"
    }
  }

  backend "s3" {
    bucket         = "jobmonitor-terraform-state"
    key            = "aws/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "job-monitoring-platform"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ---- Data Sources ----
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  name_prefix = "jobmonitor-${var.environment}"
  azs         = slice(data.aws_availability_zones.available.names, 0, 3)

  services = [
    "eureka-server", "config-server", "auth-service", "ingestion-gateway",
    "job-monitoring-service", "alerting-service", "notification-service",
    "job-queue-service", "log-ingestion-service", "db-performance-service",
    "job-worker", "notification-worker"
  ]
}

# ============================================================================
# Module: VPC
# ============================================================================
module "vpc" {
  source = "./modules/vpc"

  name_prefix = local.name_prefix
  vpc_cidr    = var.vpc_cidr
  azs         = local.azs
}

# ============================================================================
# Module: EKS Cluster
# ============================================================================
module "eks" {
  source = "./modules/eks"

  name_prefix        = local.name_prefix
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_version        = var.eks_version
  node_instance_type = var.eks_node_instance_type
  node_min_size      = var.eks_node_min_size
  node_max_size      = var.eks_node_max_size
  node_desired_size  = var.eks_node_desired_size
}

# ============================================================================
# Module: RDS (PostgreSQL 16)
# ============================================================================
module "rds" {
  source = "./modules/rds"

  name_prefix        = local.name_prefix
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_security_group = module.eks.node_security_group_id
  instance_class     = var.rds_instance_class
  allocated_storage  = var.rds_allocated_storage
  db_name            = var.db_name
  db_username        = var.db_username
  db_password        = var.db_password
  multi_az           = var.environment == "prod"
}

# ============================================================================
# Module: ElastiCache (Redis 7)
# ============================================================================
module "redis" {
  source = "./modules/redis"

  name_prefix        = local.name_prefix
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_security_group = module.eks.node_security_group_id
  node_type          = var.redis_node_type
  num_cache_nodes    = var.environment == "prod" ? 2 : 1
}

# ============================================================================
# Module: MSK (Kafka 3.7)
# ============================================================================
module "msk" {
  source = "./modules/msk"

  name_prefix        = local.name_prefix
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_security_group = module.eks.node_security_group_id
  kafka_version      = "3.7.0"
  broker_instance    = var.msk_broker_instance
  broker_count       = var.environment == "prod" ? 3 : 2
  ebs_volume_size    = var.msk_ebs_volume_size
}

# ============================================================================
# Module: Elasticsearch (OpenSearch)
# ============================================================================
module "opensearch" {
  source = "./modules/opensearch"

  name_prefix        = local.name_prefix
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  eks_security_group = module.eks.node_security_group_id
  instance_type      = var.opensearch_instance_type
  instance_count     = var.environment == "prod" ? 2 : 1
  ebs_volume_size    = var.opensearch_ebs_volume_size
}

# ============================================================================
# Module: ECR (Container Registry)
# ============================================================================
module "ecr" {
  source = "./modules/ecr"

  name_prefix = local.name_prefix
  services    = local.services
}

# ============================================================================
# Module: S3 (Log Storage)
# ============================================================================
module "s3" {
  source = "./modules/s3"

  name_prefix = local.name_prefix
  account_id  = data.aws_caller_identity.current.account_id
}

# ============================================================================
# Module: SES & SNS (Notifications)
# ============================================================================
module "notifications" {
  source = "./modules/notifications"

  name_prefix = local.name_prefix
  ses_email   = var.ses_verified_email
}

# ============================================================================
# Module: ALB (Application Load Balancer)
# ============================================================================
module "alb" {
  source = "./modules/alb"

  name_prefix       = local.name_prefix
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  certificate_arn   = var.acm_certificate_arn
}
