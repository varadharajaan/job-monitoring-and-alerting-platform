# ============================================================================
# Azure Key Vault Module
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "tenant_id" { type = string }
variable "aks_identity_id" { type = string }
variable "tags" { type = map(string) }
variable "secrets" { type = map(string) }

locals {
  kv_name = replace("kv-${var.name_prefix}", "_", "")
}

resource "azurerm_key_vault" "main" {
  name                        = substr(local.kv_name, 0, 24)
  resource_group_name         = var.resource_group_name
  location                    = var.location
  tenant_id                   = var.tenant_id
  sku_name                    = "standard"
  purge_protection_enabled    = true
  soft_delete_retention_days  = 90
  enable_rbac_authorization   = true

  network_acls {
    default_action = "Deny"
    bypass         = "AzureServices"
  }

  tags = var.tags
}

# ---- Grant AKS identity access to secrets ----
resource "azurerm_role_assignment" "aks_secrets_reader" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = var.aks_identity_id
}

# ---- Store secrets ----
resource "azurerm_key_vault_secret" "secrets" {
  for_each = var.secrets

  name         = each.key
  value        = each.value
  key_vault_id = azurerm_key_vault.main.id
}

# ---- Outputs ----
output "vault_uri" {
  value = azurerm_key_vault.main.vault_uri
}

output "vault_id" {
  value = azurerm_key_vault.main.id
}
