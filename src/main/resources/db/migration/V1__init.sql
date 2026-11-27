-- Fase 0: preparación de la base de datos.
-- Las tablas de cada módulo se crean en las migraciones de las fases siguientes.

-- Emails sin distinción entre mayúsculas y minúsculas (users.email).
CREATE EXTENSION IF NOT EXISTS citext;
