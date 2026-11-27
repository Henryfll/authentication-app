-- Módulo apps: aplicaciones, clientes, permisos y roles.

CREATE TABLE applications (
    id                UUID PRIMARY KEY,
    code              VARCHAR(50)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    description       TEXT,
    status            VARCHAR(20)  NOT NULL,
    self_registration BOOLEAN      NOT NULL DEFAULT FALSE,
    default_role_id   UUID,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_applications_code UNIQUE (code),
    CONSTRAINT ck_applications_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE application_clients (
    id                        UUID PRIMARY KEY,
    application_id            UUID         NOT NULL REFERENCES applications (id),
    client_id                 VARCHAR(100) NOT NULL,
    name                      VARCHAR(100) NOT NULL,
    platform                  VARCHAR(20)  NOT NULL,
    confidential              BOOLEAN      NOT NULL,
    client_secret_hash        VARCHAR(255),
    allowed_origins           TEXT[]       NOT NULL DEFAULT '{}',
    access_token_ttl_seconds  INT          NOT NULL,
    refresh_token_ttl_seconds INT          NOT NULL,
    status                    VARCHAR(20)  NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_application_clients_client_id UNIQUE (client_id),
    CONSTRAINT ck_application_clients_platform CHECK (platform IN ('WEB', 'MOBILE', 'SERVICE')),
    CONSTRAINT ck_application_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_application_clients_secret CHECK (confidential = (client_secret_hash IS NOT NULL))
);

CREATE INDEX ix_application_clients_application ON application_clients (application_id);

CREATE TABLE permissions (
    id             UUID PRIMARY KEY,
    application_id UUID         NOT NULL REFERENCES applications (id),
    code           VARCHAR(100) NOT NULL,
    description    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_permissions_application_code UNIQUE (application_id, code)
);

CREATE TABLE roles (
    id             UUID PRIMARY KEY,
    application_id UUID         NOT NULL REFERENCES applications (id),
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    TEXT,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_roles_application_code UNIQUE (application_id, code)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Referencia circular applications ↔ roles: se añade cuando ambas tablas existen.
ALTER TABLE applications
    ADD CONSTRAINT fk_applications_default_role FOREIGN KEY (default_role_id) REFERENCES roles (id);
