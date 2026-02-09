-- ═══════════════════════════════════════════════════════════════════
--  V004: Alert Rules + Alert History
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE alert_rules (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID            NOT NULL REFERENCES tenants(id),
    job_id              UUID            REFERENCES jobs(id),
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    rule_type           VARCHAR(50)     NOT NULL,
    condition_json      JSONB           NOT NULL,
    severity            VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    notification_channels JSONB         NOT NULL DEFAULT '["EMAIL"]',
    cooldown_seconds    INT             DEFAULT 300,
    enabled             BOOLEAN         NOT NULL DEFAULT true,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_rules_tenant  ON alert_rules(tenant_id);
CREATE INDEX idx_alert_rules_job     ON alert_rules(job_id);
CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);

CREATE TRIGGER set_alert_rules_updated_at
    BEFORE UPDATE ON alert_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Alert History (immutable log) ──
CREATE TABLE alert_history (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    alert_rule_id   UUID            NOT NULL REFERENCES alert_rules(id),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    job_id          UUID            REFERENCES jobs(id),
    severity        VARCHAR(20)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'TRIGGERED',
    message         TEXT,
    context_json    JSONB           DEFAULT '{}',
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    triggered_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),

    PRIMARY KEY (id, triggered_at)
);

SELECT create_hypertable('alert_history', 'triggered_at',
    chunk_time_interval => INTERVAL '7 days',
    migrate_data => true
);

CREATE INDEX idx_alert_hist_rule   ON alert_history(alert_rule_id, triggered_at DESC);
CREATE INDEX idx_alert_hist_tenant ON alert_history(tenant_id, triggered_at DESC);
CREATE INDEX idx_alert_hist_status ON alert_history(status, triggered_at DESC);

SELECT add_retention_policy('alert_history', INTERVAL '180 days');
