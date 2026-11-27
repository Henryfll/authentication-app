-- Módulo access: concesión de acceso de usuarios a aplicaciones y roles asignados.

CREATE TABLE user_application_access (
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id),
    application_id UUID        NOT NULL REFERENCES applications (id),
    status         VARCHAR(20) NOT NULL,
    valid_from     TIMESTAMPTZ NOT NULL,
    valid_until    TIMESTAMPTZ,
    granted_by     UUID REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_application_access UNIQUE (user_id, application_id),
    CONSTRAINT ck_user_application_access_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT ck_user_application_access_validity CHECK (valid_until IS NULL OR valid_until > valid_from)
);

CREATE INDEX ix_user_application_access_application ON user_application_access (application_id);

-- Regla de negocio (validada en el módulo access): el rol pertenece a la aplicación del acceso.
CREATE TABLE access_roles (
    access_id UUID NOT NULL REFERENCES user_application_access (id) ON DELETE CASCADE,
    role_id   UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (access_id, role_id)
);
