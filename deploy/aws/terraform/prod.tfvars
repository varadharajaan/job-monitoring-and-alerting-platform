# ============================================================================
# Production values for AWS deployment
# ============================================================================

aws_region  = "us-east-1"
environment = "prod"

# VPC
vpc_cidr = "10.0.0.0/16"

# EKS
eks_version            = "1.29"
eks_node_instance_type = "m5.xlarge"
eks_node_min_size      = 3
eks_node_max_size      = 12
eks_node_desired_size  = 3

# RDS PostgreSQL
rds_instance_class    = "db.r6g.large"
rds_allocated_storage = 100
db_name               = "jobmonitor"
db_username           = "jobmonitor"
# db_password — pass via TF_VAR_db_password or -var="db_password=xxx"

# Redis
redis_node_type = "cache.r6g.large"

# MSK Kafka
msk_broker_instance = "kafka.m5.large"
msk_ebs_volume_size = 100

# OpenSearch (Elasticsearch)
opensearch_instance_type   = "r6g.large.search"
opensearch_ebs_volume_size = 100

# Notifications
ses_verified_email = "noreply@jobmonitor.example.com"

# ALB / TLS
# acm_certificate_arn = "arn:aws:acm:us-east-1:ACCOUNT:certificate/UUID"
