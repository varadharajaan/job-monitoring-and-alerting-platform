# ============================================================================
# Azure Input Variables
# ============================================================================

variable "azure_region" {
  description = "Azure region to deploy into"
  type        = string
  default     = "eastus"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "prod"
}

# ---- VNet ----
variable "vnet_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# ---- AKS ----
variable "kubernetes_version" {
  type    = string
  default = "1.29"
}

variable "aks_node_vm_size" {
  type    = string
  default = "Standard_DS3_v2"
}

variable "aks_node_min_count" {
  type    = number
  default = 3
}

variable "aks_node_max_count" {
  type    = number
  default = 10
}

# ---- PostgreSQL ----
variable "pg_sku_name" {
  description = "Azure PostgreSQL Flexible Server SKU"
  type        = string
  default     = "GP_Standard_D4ds_v4"
}

variable "pg_storage_mb" {
  type    = number
  default = 131072  # 128 GB
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
variable "redis_capacity" {
  type    = number
  default = 1
}

# ---- Event Hubs ----
variable "eventhubs_capacity" {
  description = "Throughput units for Event Hubs"
  type        = number
  default     = 2
}
