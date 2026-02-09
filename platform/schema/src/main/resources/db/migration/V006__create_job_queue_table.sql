-- ═══════════════════════════════════════════════════════════════════
--  V006: Job Queue — Background task processing with priority & locking
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE job_queue (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    queue_name      VARCHAR(100)    NOT NULL DEFAULT 'default',
    job_type        VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    priority        INT             NOT NULL DEFAULT 5,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    max_retries     INT             NOT NULL DEFAULT 3,
    retry_count     INT             NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    locked_by       VARCHAR(255),
    locked_at       TIMESTAMPTZ,
    lock_expires_at TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    result          JSONB,
    timeout_seconds INT             DEFAULT 3600,
    metadata        JSONB           DEFAULT '{}',
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_queue_tenant        ON job_queue(tenant_id);
CREATE INDEX idx_queue_status_prio   ON job_queue(status, priority DESC, created_at ASC)
    WHERE status IN ('PENDING', 'RETRY');
CREATE INDEX idx_queue_locked        ON job_queue(locked_by, lock_expires_at)
    WHERE locked_by IS NOT NULL;
CREATE INDEX idx_queue_name          ON job_queue(queue_name, status);
CREATE INDEX idx_queue_next_retry    ON job_queue(next_retry_at)
    WHERE status = 'RETRY' AND next_retry_at IS NOT NULL;

CREATE TRIGGER set_job_queue_updated_at
    BEFORE UPDATE ON job_queue
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── Dead letter table for permanently failed queue items ──
CREATE TABLE job_queue_dead_letter (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    original_id     UUID            NOT NULL,
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    queue_name      VARCHAR(100)    NOT NULL,
    job_type        VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    error_message   TEXT,
    retry_count     INT             NOT NULL,
    metadata        JSONB           DEFAULT '{}',
    failed_at       TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_dlq_tenant ON job_queue_dead_letter(tenant_id, failed_at DESC);
