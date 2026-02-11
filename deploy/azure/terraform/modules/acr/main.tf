# ============================================================================
# Azure Container Registry Module
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "sku" { type = string }
variable "tags" { type = map(string) }

locals {
  # ACR names: alphanumeric, 5-50 chars
  acr_name = replace(lower("acr${var.name_prefix}"), "-", "")
}

resource "azurerm_container_registry" "main" {
  name                = substr(local.acr_name, 0, 50)
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku
  admin_enabled       = false

  # Premium features
  dynamic "georeplications" {
    for_each = var.sku == "Premium" ? ["westus2"] : []
    content {
      location = georeplications.value
    }
  }

  dynamic "retention_policy" {
    for_each = var.sku == "Premium" ? [1] : []
    content {
      days    = 30
      enabled = true
    }
  }

  tags = var.tags
}

# ---- Outputs ----
output "acr_id" {
  value = azurerm_container_registry.main.id
}

output "login_server" {
  value = azurerm_container_registry.main.login_server
}
