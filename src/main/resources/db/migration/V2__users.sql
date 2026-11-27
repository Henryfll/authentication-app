-- Módulo user: identidad de usuarios.

CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    email                 CITEXT       NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100),
    last_name             VARCHAR(100),
    status                VARCHAR(20)  NOT NULL,
    email_verified_at     TIMESTAMPTZ,              -- reservado para la verificación de email (futuro)
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX ix_users_status ON users (status);
