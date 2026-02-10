-- ═══════════════════════════════════════════════════════════════════
--  V007: Users & API Keys — Authentication & Authorization
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    username        VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255),
    role            VARCHAR(50)     NOT NULL DEFAULT 'VIEWER',
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    last_login_at   TIMESTAMPTZ,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER'))
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

CREATE TRIGGER set_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ── API Keys for service-to-service and external integrations ──
CREATE TABLE api_keys (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID            NOT NULL REFERENCES tenants(id),
    user_id         UUID            NOT NULL REFERENCES users(id),
    name            VARCHAR(255)    NOT NULL,
    key_hash        VARCHAR(255)    NOT NULL,
    key_prefix      VARCHAR(10)     NOT NULL,
    scopes          JSONB           NOT NULL DEFAULT '["READ"]',
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT uq_apikeys_name_tenant UNIQUE (tenant_id, name)
);

CREATE INDEX idx_apikeys_tenant ON api_keys(tenant_id);
CREATE INDEX idx_apikeys_user ON api_keys(user_id);
CREATE INDEX idx_apikeys_prefix ON api_keys(key_prefix);

CREATE TRIGGER set_api_keys_updated_at
    BEFORE UPDATE ON api_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
