# ============================================================================
# Azure Monitor + Application Insights Module
# ============================================================================

variable "name_prefix" { type = string }
variable "resource_group_name" { type = string }
variable "location" { type = string }
variable "tags" { type = map(string) }

# ---- Log Analytics Workspace ----
resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = var.tags
}

# ---- Application Insights ----
resource "azurerm_application_insights" "main" {
  name                = "ai-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  location            = var.location
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "java"
  tags                = var.tags
}

# ---- Action Group for Alerts ----
resource "azurerm_monitor_action_group" "critical" {
  name                = "ag-critical-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  short_name          = "critical"
  tags                = var.tags

  email_receiver {
    name                    = "ops-team"
    email_address           = "ops@jobmonitor.com"
    use_common_alert_schema = true
  }
}

# ---- Metric Alert: High CPU ----
resource "azurerm_monitor_metric_alert" "high_cpu" {
  name                = "alert-high-cpu-${var.name_prefix}"
  resource_group_name = var.resource_group_name
  scopes              = [azurerm_application_insights.main.id]
  description         = "Alert when CPU > 80% for 5 minutes"
  frequency           = "PT1M"
  window_size         = "PT5M"
  severity            = 2

  criteria {
    metric_namespace = "microsoft.insights/components"
    metric_name      = "performanceCounters/processCpuPercentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = azurerm_monitor_action_group.critical.id
  }

  tags = var.tags
}

# ---- Outputs ----
output "log_analytics_workspace_id" {
  value = azurerm_log_analytics_workspace.main.id
}

output "app_insights_id" {
  value = azurerm_application_insights.main.id
}

output "app_insights_instrumentation_key" {
  value     = azurerm_application_insights.main.instrumentation_key
  sensitive = true
}

output "app_insights_connection_string" {
  value     = azurerm_application_insights.main.connection_string
  sensitive = true
}
