-- ═══════════════════════════════════════════════════════════════════
--  V002: Jobs — Registered scheduled jobs to monitor
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    cron_expression VARCHAR(100),
    schedule_type   VARCHAR(50)     NOT NULL DEFAULT 'CRON',
    sla_seconds     INT,
    grace_period_seconds INT        DEFAULT 300,
    expected_runtime_seconds INT,
    timeout_seconds INT             DEFAULT 3600,
    max_retries     INT             DEFAULT 3,
    tags            JSONB           DEFAULT '[]',
    metadata        JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_job_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_jobs_tenant     ON jobs(tenant_id);
CREATE INDEX idx_jobs_status     ON jobs(status);
CREATE INDEX idx_jobs_tags       ON jobs USING GIN(tags);

CREATE TRIGGER set_jobs_updated_at
    BEFORE UPDATE ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
