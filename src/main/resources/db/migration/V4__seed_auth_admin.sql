-- La propia plataforma se registra como la aplicación "auth-admin" (docs/ARCHITECTURE.md §5.1 y §7.3).
-- Los identificadores son fijos para poder referenciarlos desde migraciones posteriores.

INSERT INTO applications (id, code, name, description, status, self_registration, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'auth-admin', 'Auth Admin',
        'Consola de administración de la plataforma de autenticación', 'ACTIVE', FALSE, now(), now());

INSERT INTO application_clients (id, application_id, client_id, name, platform, confidential,
                                 allowed_origins, access_token_ttl_seconds, refresh_token_ttl_seconds,
                                 status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001',
        'auth-admin-web', 'Auth Admin Web', 'WEB', FALSE, '{}', 900, 604800, 'ACTIVE', now(), now());

INSERT INTO permissions (id, application_id, code, description, created_at) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001', 'applications:read',  'Consultar aplicaciones, clientes, roles y permisos', now()),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000001', 'applications:write', 'Gestionar aplicaciones, clientes, roles y permisos', now()),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000001', 'users:read',         'Consultar usuarios', now()),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000001', 'users:write',        'Gestionar usuarios', now()),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000001', 'access:read',        'Consultar accesos', now()),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000001', 'access:write',       'Conceder y revocar accesos', now()),
    ('00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000001', 'audit:read',         'Consultar la auditoría', now());

INSERT INTO roles (id, application_id, code, name, description, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000001', 'SUPER_ADMIN',    'Super administrador', 'Acceso total a la plataforma', now(), now()),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000001', 'APP_MANAGER',    'Gestor de aplicaciones', 'Gestiona aplicaciones, clientes, roles y permisos', now(), now()),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000001', 'ACCESS_MANAGER', 'Gestor de accesos', 'Gestiona usuarios y concesiones de acceso', now(), now()),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000001', 'AUDITOR',        'Auditor', 'Solo lectura de la auditoría', now(), now());

-- SUPER_ADMIN: todos los permisos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000301', id FROM permissions
WHERE application_id = '00000000-0000-0000-0000-000000000001';

INSERT INTO role_permissions (role_id, permission_id) VALUES
    -- APP_MANAGER
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000201'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000202'),
    -- ACCESS_MANAGER
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000201'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000203'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000204'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000205'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000206'),
    -- AUDITOR
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000207');
