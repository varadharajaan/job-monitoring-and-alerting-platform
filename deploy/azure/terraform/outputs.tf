# ============================================================================
# Azure Outputs
# ============================================================================

output "resource_group" {
  value = azurerm_resource_group.main.name
}

output "aks_cluster_name" {
  value = module.aks.cluster_name
}

output "acr_login_server" {
  value = module.acr.login_server
}

output "postgresql_fqdn" {
  value = module.postgresql.fqdn
}

output "postgresql_connection_url" {
  value     = "jdbc:postgresql://${module.postgresql.fqdn}:5432/${var.db_name}?sslmode=require"
  sensitive = true
}

output "redis_hostname" {
  value = module.redis.hostname
}

output "redis_ssl_port" {
  value = module.redis.ssl_port
}

output "eventhubs_bootstrap_servers" {
  value = module.eventhubs.kafka_bootstrap_servers
}

output "eventhubs_connection_string" {
  value     = module.eventhubs.primary_connection_string
  sensitive = true
}

output "storage_account_name" {
  value = module.storage.account_name
}

output "app_insights_connection_string" {
  value     = module.monitoring.app_insights_connection_string
  sensitive = true
}

output "keyvault_uri" {
  value = module.keyvault.vault_uri
}

output "kubeconfig_command" {
  value = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${module.aks.cluster_name}"
}
