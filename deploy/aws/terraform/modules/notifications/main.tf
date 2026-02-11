# ============================================================================
# Notifications Module — SES (Email) + SNS (SMS/Push)
# ============================================================================

variable "name_prefix" { type = string }
variable "ses_email" { type = string }

# ---- SES Email Identity ----
resource "aws_ses_email_identity" "sender" {
  email = var.ses_email
}

# ---- SNS Topic for alerts ----
resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"
}

resource "aws_sns_topic" "sms" {
  name = "${var.name_prefix}-sms-notifications"
}

output "ses_email" { value = aws_ses_email_identity.sender.email }
output "sns_alerts_arn" { value = aws_sns_topic.alerts.arn }
output "sns_sms_arn" { value = aws_sns_topic.sms.arn }
