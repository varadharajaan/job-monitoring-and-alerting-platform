-- ═══════════════════════════════════════════════════════════════════
--  V005: Notifications — Multi-channel delivery tracking
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE notification_templates (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID            NOT NULL REFERENCES tenants(id),
    name        VARCHAR(255)    NOT NULL,
    channel     VARCHAR(20)     NOT NULL,
    subject     VARCHAR(500),
    body        TEXT            NOT NULL,
    variables   JSONB           DEFAULT '[]',
    version     BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_template_tenant_name_channel UNIQUE (tenant_id, name, channel)
);

CREATE INDEX idx_templates_tenant ON notification_templates(tenant_id);

CREATE TABLE notifications (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    template_id     UUID            REFERENCES notification_templates(id),
    channel         VARCHAR(20)     NOT NULL,
    recipient       VARCHAR(500)    NOT NULL,
    subject         VARCHAR(500),
    body            TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    priority        INT             NOT NULL DEFAULT 5,
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retries     INT             NOT NULL DEFAULT 3,
    error_message   TEXT,
    metadata        JSONB           DEFAULT '{}',
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    PRIMARY KEY (id, created_at)
);

SELECT create_hypertable('notifications', 'created_at',
    chunk_time_interval => INTERVAL '7 days',
    migrate_data => true
);

CREATE INDEX idx_notif_tenant  ON notifications(tenant_id, created_at DESC);
CREATE INDEX idx_notif_status  ON notifications(status, created_at DESC);
CREATE INDEX idx_notif_channel ON notifications(channel, created_at DESC);

SELECT add_retention_policy('notifications', INTERVAL '90 days');
