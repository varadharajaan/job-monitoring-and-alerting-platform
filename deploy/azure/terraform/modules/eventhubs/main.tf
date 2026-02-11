# ============================================================================
# Azure Event Hubs (Kafka-compatible) Module
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "sku" { type = string }
variable "capacity" { type = number }
variable "tags" { type = map(string) }
variable "topics" { type = list(string) }

# ---- Event Hubs Namespace (Kafka-enabled) ----
resource "azurerm_eventhub_namespace" "main" {
  name                     = "ehns-${var.name_prefix}"
  resource_group_name      = var.resource_group_name
  location                 = var.location
  sku                      = var.sku
  capacity                 = var.capacity
  auto_inflate_enabled     = var.sku == "Standard" ? true : false
  maximum_throughput_units = var.sku == "Standard" ? 10 : 0
  tags                     = var.tags

  # Kafka is automatically enabled for Standard/Premium tier
}

# ---- Shared Access Policy (send + listen) ----
resource "azurerm_eventhub_namespace_authorization_rule" "app" {
  name                = "app-access"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = var.resource_group_name

  listen = true
  send   = true
  manage = false
}

# ---- Event Hubs (Kafka topics) ----
resource "azurerm_eventhub" "topics" {
  for_each = toset(var.topics)

  name                = each.value
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = var.resource_group_name
  partition_count     = 4
  message_retention   = var.sku == "Standard" ? 7 : 1
}

# ---- Consumer Groups ----
resource "azurerm_eventhub_consumer_group" "app" {
  for_each = toset(var.topics)

  name                = "jobmonitor-cg"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.topics[each.key].name
  resource_group_name = var.resource_group_name
}

# ---- Outputs ----
output "namespace_name" {
  value = azurerm_eventhub_namespace.main.name
}

output "kafka_bootstrap_servers" {
  value = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
}

output "primary_connection_string" {
  value     = azurerm_eventhub_namespace_authorization_rule.app.primary_connection_string
  sensitive = true
}
