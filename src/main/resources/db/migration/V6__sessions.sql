-- Módulo auth: sesiones (una por login) y refresh tokens con rotación.

CREATE TABLE sessions (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id),
    application_id UUID         NOT NULL REFERENCES applications (id),
    client_id      UUID         NOT NULL REFERENCES application_clients (id),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    device_name    VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL,
    last_used_at   TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(30),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sessions_revoked CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT ck_sessions_reason CHECK (revoked_reason IN
        ('LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED', 'ADMIN', 'ACCESS_REVOKED', 'USER_INACTIVE'))
);

CREATE INDEX ix_sessions_user_active ON sessions (user_id) WHERE revoked_at IS NULL;

-- Solo se guarda el hash SHA-256 del refresh token, nunca su valor.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    session_id  UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    replaced_by UUID REFERENCES refresh_tokens (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);
