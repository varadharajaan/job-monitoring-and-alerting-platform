-- ═══════════════════════════════════════════════════════════════════
--  V1__init_log_ingestion.sql — Log Ingestion Service schema
-- ═══════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS log_ingestion;

-- ── Log Alert Patterns ──
CREATE TABLE log_ingestion.log_alert_patterns (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(128)  NOT NULL,
    name                  VARCHAR(256)  NOT NULL,
    regex_pattern         TEXT          NOT NULL,
    min_level             VARCHAR(16)   NOT NULL DEFAULT 'ERROR',
    service_filter        VARCHAR(256),
    enabled               BOOLEAN       NOT NULL DEFAULT TRUE,
    notification_channel  VARCHAR(64),
    notification_recipient VARCHAR(512),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_log_alert_patterns_tenant ON log_ingestion.log_alert_patterns (tenant_id, enabled);

-- ── Log Retention Policies ──
CREATE TABLE log_ingestion.log_retention_policies (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(128)  NOT NULL UNIQUE,
    retention_days   INTEGER       NOT NULL DEFAULT 30,
    max_storage_gb   BIGINT        NOT NULL DEFAULT 10,
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_log_retention_tenant ON log_ingestion.log_retention_policies (tenant_id);
