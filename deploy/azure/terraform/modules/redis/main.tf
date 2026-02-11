# ============================================================================
# Azure Cache for Redis Module
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "redis_subnet_id" { type = string }
variable "sku_name" { type = string }
variable "capacity" { type = number }
variable "tags" { type = map(string) }

resource "azurerm_redis_cache" "main" {
  name                = "redis-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  location            = var.location
  capacity            = var.capacity
  family              = var.sku_name == "Premium" ? "P" : "C"
  sku_name            = var.sku_name
  enable_non_ssl_port = false
  minimum_tls_version = "1.2"

  redis_configuration {
    maxmemory_reserved = 50
    maxmemory_delta    = 50
    maxmemory_policy   = "volatile-lru"
  }

  # Premium only — VNet injection
  dynamic "patch_schedule" {
    for_each = var.sku_name == "Premium" ? [1] : []
    content {
      day_of_week    = "Sunday"
      start_hour_utc = 3
    }
  }

  subnet_id = var.sku_name == "Premium" ? var.redis_subnet_id : null

  tags = var.tags
}

# ---- Outputs ----
output "hostname" {
  value = azurerm_redis_cache.main.hostname
}

output "ssl_port" {
  value = azurerm_redis_cache.main.ssl_port
}

output "primary_key" {
  value     = azurerm_redis_cache.main.primary_access_key
  sensitive = true
}

output "primary_connection_string" {
  value     = azurerm_redis_cache.main.primary_connection_string
  sensitive = true
}
