-- ═══════════════════════════════════════════════════════════════════
--  V1__init_db_performance.sql — DB Performance Service schema
-- ═══════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS db_performance;

-- ── Monitored Databases ──
CREATE TABLE db_performance.monitored_databases (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(128)  NOT NULL,
    name                    VARCHAR(256)  NOT NULL,
    jdbc_url                VARCHAR(1024) NOT NULL,
    username                VARCHAR(128)  NOT NULL,
    encrypted_password      VARCHAR(512)  NOT NULL,
    db_type                 VARCHAR(32)   NOT NULL DEFAULT 'POSTGRESQL',
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    slow_query_threshold_ms BIGINT        NOT NULL DEFAULT 1000,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_monitored_dbs_tenant ON db_performance.monitored_databases (tenant_id, enabled);

-- ── Slow Queries ──
CREATE TABLE db_performance.slow_queries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    database_id       UUID          NOT NULL REFERENCES db_performance.monitored_databases(id) ON DELETE CASCADE,
    tenant_id         VARCHAR(128)  NOT NULL,
    query_text        TEXT          NOT NULL,
    query_fingerprint VARCHAR(64)   NOT NULL,
    mean_time_ms      DOUBLE PRECISION NOT NULL,
    max_time_ms       DOUBLE PRECISION NOT NULL,
    calls             BIGINT        NOT NULL DEFAULT 0,
    total_rows        BIGINT        NOT NULL DEFAULT 0,
    shared_blks_hit   BIGINT        DEFAULT 0,
    shared_blks_read  BIGINT        DEFAULT 0,
    captured_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_slow_queries_db ON db_performance.slow_queries (database_id, mean_time_ms DESC);
CREATE INDEX idx_slow_queries_tenant ON db_performance.slow_queries (tenant_id);
CREATE INDEX idx_slow_queries_fingerprint ON db_performance.slow_queries (database_id, query_fingerprint);

-- ── Index Suggestions ──
CREATE TABLE db_performance.index_suggestions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    database_id              UUID          NOT NULL REFERENCES db_performance.monitored_databases(id) ON DELETE CASCADE,
    tenant_id                VARCHAR(128)  NOT NULL,
    table_name               VARCHAR(256)  NOT NULL,
    column_name              VARCHAR(256)  NOT NULL,
    suggested_ddl            TEXT          NOT NULL,
    reason                   TEXT,
    estimated_improvement_pct DOUBLE PRECISION,
    status                   VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_index_suggestions_db ON db_performance.index_suggestions (database_id, status);
CREATE INDEX idx_index_suggestions_tenant ON db_performance.index_suggestions (tenant_id);
