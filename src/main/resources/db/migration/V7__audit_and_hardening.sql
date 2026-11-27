-- Fase 5: auditoría de eventos de seguridad.
-- Sin claves foráneas: el registro de auditoría debe conservarse aunque cambien los datos.

CREATE TABLE audit_events (
    id             UUID PRIMARY KEY,
    type           VARCHAR(50)  NOT NULL,
    actor_user_id  UUID,
    target_user_id UUID,
    application_id UUID,
    ip_address     VARCHAR(45),
    details        TEXT,        -- JSON con datos adicionales del evento
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_audit_events_created_at ON audit_events (created_at DESC);
CREATE INDEX ix_audit_events_target_user ON audit_events (target_user_id, created_at DESC);
CREATE INDEX ix_audit_events_type ON audit_events (type, created_at DESC);

-- Nuevo motivo de revocación: cambio o restablecimiento de contraseña.
ALTER TABLE sessions DROP CONSTRAINT ck_sessions_reason;
ALTER TABLE sessions ADD CONSTRAINT ck_sessions_reason CHECK (revoked_reason IN
    ('LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED', 'ADMIN', 'ACCESS_REVOKED', 'USER_INACTIVE',
     'PASSWORD_CHANGED'));
