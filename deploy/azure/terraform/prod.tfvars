# ============================================================================
# Production values for Azure deployment
# ============================================================================

azure_region = "eastus"
environment  = "prod"

# VNet
vnet_cidr = "10.0.0.0/16"

# AKS
kubernetes_version = "1.29"
aks_node_vm_size   = "Standard_DS3_v2"
aks_node_min_count = 3
aks_node_max_count = 10

# PostgreSQL Flexible Server
pg_sku_name   = "GP_Standard_D4ds_v4"
pg_storage_mb = 131072  # 128 GB
db_name       = "jobmonitor"
db_username   = "jobmonitor"
# db_password — pass via TF_VAR_db_password or -var="db_password=xxx"

# Redis
redis_capacity = 1  # Premium P1

# Event Hubs
eventhubs_capacity = 2  # 2 throughput units
