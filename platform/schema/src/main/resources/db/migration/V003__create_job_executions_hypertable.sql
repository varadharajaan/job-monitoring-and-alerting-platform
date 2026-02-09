-- ═══════════════════════════════════════════════════════════════════
--  V003: Job Executions — TimescaleDB hypertable for time-series data
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE job_executions (
    id              UUID            NOT NULL DEFAULT uuid_generate_v4(),
    job_id          UUID            NOT NULL REFERENCES jobs(id),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    duration_ms     BIGINT,
    exit_code       INT,
    output          TEXT,
    error_message   TEXT,
    attempt_number  INT             NOT NULL DEFAULT 1,
    metadata        JSONB           DEFAULT '{}',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    PRIMARY KEY (id, started_at)
);

-- ── Convert to TimescaleDB hypertable for efficient time-series queries ──
SELECT create_hypertable('job_executions', 'started_at',
    chunk_time_interval => INTERVAL '7 days',
    migrate_data => true
);

CREATE INDEX idx_exec_job          ON job_executions(job_id, started_at DESC);
CREATE INDEX idx_exec_tenant       ON job_executions(tenant_id, started_at DESC);
CREATE INDEX idx_exec_status       ON job_executions(status, started_at DESC);

-- ── Continuous aggregate for dashboard queries ──
CREATE MATERIALIZED VIEW job_execution_hourly
WITH (timescaledb.continuous) AS
SELECT
    job_id,
    tenant_id,
    time_bucket('1 hour', started_at) AS bucket,
    COUNT(*)                          AS total_runs,
    COUNT(*) FILTER (WHERE status = 'SUCCESS')  AS success_count,
    COUNT(*) FILTER (WHERE status = 'FAILED')   AS failure_count,
    AVG(duration_ms)                  AS avg_duration_ms,
    MAX(duration_ms)                  AS max_duration_ms,
    MIN(duration_ms)                  AS min_duration_ms,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms
FROM job_executions
GROUP BY job_id, tenant_id, bucket
WITH NO DATA;

-- Refresh policy: keep continuous aggregate updated
SELECT add_continuous_aggregate_policy('job_execution_hourly',
    start_offset    => INTERVAL '3 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour'
);

-- ── Data retention policy: auto-drop raw data older than configured period ──
SELECT add_retention_policy('job_executions', INTERVAL '90 days');
