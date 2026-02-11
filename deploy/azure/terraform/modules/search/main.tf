# ============================================================================
# Azure Cognitive Search Module (Elasticsearch alternative)
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "sku" { type = string }
variable "tags" { type = map(string) }

resource "azurerm_search_service" "main" {
  name                = "srch-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku
  replica_count       = var.sku == "standard" ? 2 : 1
  partition_count     = 1

  public_network_access_enabled = false

  tags = var.tags
}

# ---- Outputs ----
output "search_service_name" {
  value = azurerm_search_service.main.name
}

output "search_service_id" {
  value = azurerm_search_service.main.id
}

output "primary_key" {
  value     = azurerm_search_service.main.primary_key
  sensitive = true
}

output "query_keys" {
  value = azurerm_search_service.main.query_keys
}
