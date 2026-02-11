# ============================================================================
# Input Variables
# ============================================================================

variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

# ---- VPC ----
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# ---- EKS ----
variable "eks_version" {
  description = "Kubernetes version for EKS"
  type        = string
  default     = "1.29"
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for EKS worker nodes"
  type        = string
  default     = "m5.xlarge"
}

variable "eks_node_min_size" {
  type    = number
  default = 2
}

variable "eks_node_max_size" {
  type    = number
  default = 10
}

variable "eks_node_desired_size" {
  type    = number
  default = 3
}

# ---- RDS ----
variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r6g.large"
}

variable "rds_allocated_storage" {
  type    = number
  default = 100
}

variable "db_name" {
  type    = string
  default = "jobmonitor"
}

variable "db_username" {
  type      = string
  default   = "jobmonitor"
  sensitive = true
}

variable "db_password" {
  type      = string
  sensitive = true
}

# ---- Redis ----
variable "redis_node_type" {
  type    = string
  default = "cache.r6g.large"
}

# ---- MSK (Kafka) ----
variable "msk_broker_instance" {
  type    = string
  default = "kafka.m5.large"
}

variable "msk_ebs_volume_size" {
  type    = number
  default = 100
}

# ---- OpenSearch (Elasticsearch) ----
variable "opensearch_instance_type" {
  type    = string
  default = "r6g.large.search"
}

variable "opensearch_ebs_volume_size" {
  type    = number
  default = 100
}

# ---- Notifications ----
variable "ses_verified_email" {
  description = "SES verified sender email"
  type        = string
  default     = "noreply@jobmonitor.example.com"
}

# ---- ALB / TLS ----
variable "acm_certificate_arn" {
  description = "ACM certificate ARN for HTTPS on ALB"
  type        = string
  default     = ""
}
