# ============================================================================
# Azure Infrastructure for Job Monitoring & Alerting Platform
# ============================================================================
# Usage:
#   cd deploy/azure/terraform
#   terraform init
#   terraform plan -var-file="prod.tfvars"
#   terraform apply -var-file="prod.tfvars"
# ============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.90"
    }
  }

  backend "azurerm" {
    resource_group_name  = "rg-jobmonitor-tfstate"
    storage_account_name = "jobmonitortfstate"
    container_name       = "tfstate"
    key                  = "azure/terraform.tfstate"
  }
}

provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy = false
    }
  }
}

# ---- Data Sources ----
data "azurerm_client_config" "current" {}

locals {
  name_prefix = "jobmonitor-${var.environment}"

  services = [
    "eureka-server", "config-server", "auth-service", "ingestion-gateway",
    "job-monitoring-service", "alerting-service", "notification-service",
    "job-queue-service", "log-ingestion-service", "db-performance-service",
    "job-worker", "notification-worker"
  ]

  common_tags = {
    Project     = "job-monitoring-platform"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ============================================================================
# Resource Group
# ============================================================================
resource "azurerm_resource_group" "main" {
  name     = "rg-${local.name_prefix}"
  location = var.azure_region
  tags     = local.common_tags
}

# ============================================================================
# Module: Virtual Network
# ============================================================================
module "vnet" {
  source = "./modules/vnet"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  vnet_cidr           = var.vnet_cidr
  tags                = local.common_tags
}

# ============================================================================
# Module: AKS Cluster
# ============================================================================
module "aks" {
  source = "./modules/aks"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  aks_subnet_id       = module.vnet.aks_subnet_id
  kubernetes_version  = var.kubernetes_version
  node_vm_size        = var.aks_node_vm_size
  node_min_count      = var.aks_node_min_count
  node_max_count      = var.aks_node_max_count
  acr_id              = module.acr.acr_id
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Container Registry
# ============================================================================
module "acr" {
  source = "./modules/acr"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.environment == "prod" ? "Premium" : "Standard"
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Database for PostgreSQL — Flexible Server
# ============================================================================
module "postgresql" {
  source = "./modules/postgresql"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  db_subnet_id        = module.vnet.db_subnet_id
  dns_zone_vnet_id    = module.vnet.vnet_id
  sku_name            = var.pg_sku_name
  storage_mb          = var.pg_storage_mb
  db_name             = var.db_name
  admin_username      = var.db_username
  admin_password      = var.db_password
  ha_enabled          = var.environment == "prod"
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Cache for Redis
# ============================================================================
module "redis" {
  source = "./modules/redis"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  redis_subnet_id     = module.vnet.redis_subnet_id
  sku_name            = var.environment == "prod" ? "Premium" : "Standard"
  capacity            = var.redis_capacity
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Event Hubs (Kafka-compatible)
# ============================================================================
module "eventhubs" {
  source = "./modules/eventhubs"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.environment == "prod" ? "Standard" : "Basic"
  capacity            = var.eventhubs_capacity
  tags                = local.common_tags

  topics = [
    "job-events", "alert-events", "notification-events",
    "queue-events", "log-events", "dead-letter"
  ]
}

# ============================================================================
# Module: Azure Blob Storage
# ============================================================================
module "storage" {
  source = "./modules/storage"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Cognitive Search (Elasticsearch alternative)
# ============================================================================
module "search" {
  source = "./modules/search"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.environment == "prod" ? "standard" : "basic"
  tags                = local.common_tags
}

# ============================================================================
# Module: Azure Monitor + Application Insights
# ============================================================================
module "monitoring" {
  source = "./modules/monitoring"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.common_tags
}

# ============================================================================
# Module: Key Vault (secrets management)
# ============================================================================
module "keyvault" {
  source = "./modules/keyvault"

  name_prefix         = local.name_prefix
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tenant_id           = data.azurerm_client_config.current.tenant_id
  aks_identity_id     = module.aks.kubelet_identity_object_id
  tags                = local.common_tags

  secrets = {
    "db-password"            = var.db_password
    "redis-password"         = module.redis.primary_key
    "eventhubs-conn-string"  = module.eventhubs.primary_connection_string
    "storage-account-key"    = module.storage.primary_access_key
  }
}
