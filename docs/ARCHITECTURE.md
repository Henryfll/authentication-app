# Arquitectura — Authentication App

> Estado: **Propuesta aprobada para v1** · Última actualización: 2026-11-27

## 1. Objetivo

Servicio centralizado de **identidad y control de acceso** que:

1. Gestiona el ciclo de vida de los usuarios (registro, login, sesiones, contraseñas).
2. Controla el acceso de esos usuarios a **N aplicaciones** registradas en la plataforma.
3. Define, por cada aplicación, sus **roles y permisos**, y emite tokens con la autorización correspondiente.
4. Permite que cualquier aplicación cliente (frontend web, app móvil o backend) valide los tokens de forma autónoma mediante la clave pública (JWKS), sin consultar al servicio en cada petición.

## 2. Decisiones confirmadas

| Tema | Decisión |
|---|---|
| Lenguaje | **Java 25 (LTS)** |
| Framework | **Spring Boot 4.0.x** (Spring Security 7) |
| Build | **Maven** (con wrapper `mvnw`) |
| Consumidores | **Frontend web y aplicaciones móviles** (ambos) |
| Verificación de email | **Fuera de v1** — se implementará en una versión futura |
| Rama de trabajo | `developer` |

## 3. Stack tecnológico

| Capa | Tecnología | Motivo |
|---|---|---|
| Base de datos | PostgreSQL 17 | Robusta, `uuid`, `citext`, `jsonb` |
| Persistencia | Spring Data JPA (Hibernate) | Productividad en un dominio relacional |
| Migraciones | Flyway | Esquema versionado y reproducible |
| Tokens | Spring Security OAuth2 Resource Server (Nimbus JOSE) — **JWT RS256** | Firma asimétrica: las aplicaciones validan con la clave pública |
| Hash de contraseñas | Argon2id vía `DelegatingPasswordEncoder` | Recomendación OWASP; permite migrar de algoritmo |
| Validación | Jakarta Bean Validation | Integrado en Spring MVC |
| Mapeo | Mappers explícitos en cada adaptador | El dominio es inmutable y se construye con fábricas (`create`/`restore`); el mapeo manual es más claro que MapStruct en este caso |
| Rate limiting | Bucket4j | Protección contra fuerza bruta |
| Documentación de la API | springdoc-openapi (Swagger UI) | API autodocumentada |
| Observabilidad | Actuator + Micrometer, logs JSON | Health checks, métricas, auditoría |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc, ArchUnit | Integración contra una base de datos real y reglas de arquitectura |
| Calidad | Spotless (Google Java Format), JaCoCo ≥ 80 % | Formato consistente y cobertura medida |
| Entorno local | Docker Compose (PostgreSQL) | Levantar el entorno con un solo comando |
| CI | GitHub Actions | Build, tests y cobertura en cada PR |

> **Spring Authorization Server**: no se usa en v1. El contrato de tokens (`iss`, `aud`, `azp`, JWKS) se diseña compatible con OAuth2/OIDC para poder migrar en el futuro (SSO, `authorization_code` + PKCE) sin romper a los clientes.

## 4. Estilo arquitectónico

**Arquitectura hexagonal (puertos y adaptadores)**, organizada por módulos funcionales (*bounded contexts*) dentro de un único artefacto Maven (monolito modular).

```
com.henryfll.auth
├── AuthApplication.java
├── user/                     # Identidad de usuarios
├── apps/                     # Aplicaciones, clientes, roles y permisos
├── access/                   # Concesión de acceso usuario ↔ aplicación y roles asignados
├── auth/                     # Login, tokens, sesiones, refresh, logout
└── shared/                   # Errores (RFC 9457), configuración, auditoría, utilidades
```

Cada módulo sigue la misma estructura interna:

```
<modulo>/
├── domain/                   # Entidades, value objects, reglas de negocio (Java puro)
├── application/
│   ├── port/in/              # Casos de uso (interfaces)
│   ├── port/out/             # Repositorios, servicios externos (interfaces)
│   └── service/              # Implementación de los casos de uso
└── infrastructure/
    ├── web/                  # Controllers REST + DTOs (records)
    └── persistence/          # Entidades JPA + adaptadores de repositorio
```

**Reglas, verificadas con ArchUnit**

- `domain` no depende de Spring ni de JPA.
- `application` no depende de `infrastructure`.
- Los controllers solo invocan puertos de entrada (`port.in`).
- Un módulo solo accede a otro a través de sus puertos de entrada, nunca a su persistencia.

### 4.1 Responsabilidades de cada módulo

| Módulo | Responsabilidad |
|---|---|
| **user** | Alta de usuarios, perfil, cambio de contraseña, estado de la cuenta (activo, bloqueado, deshabilitado) |
| **apps** | Registro de aplicaciones, sus clientes (web, móvil, servicio), el catálogo de permisos y los roles de cada aplicación |
| **access** | Qué usuarios pueden entrar a qué aplicaciones, con qué roles y hasta cuándo |
| **auth** | Autenticación, emisión de JWT con los roles y permisos de la aplicación, sesiones, rotación de refresh tokens y JWKS |

## 5. Modelo de dominio: control de acceso multi-aplicación

### 5.1 Conceptos

- **Application**: producto o sistema protegido (p. ej. `erp`, `crm`, `portal-clientes`). Es la unidad de autorización: los roles y permisos pertenecen a una aplicación.
- **ApplicationClient**: forma concreta de consumir una aplicación. Una aplicación puede tener varios clientes: `WEB`, `MOBILE`, `SERVICE`. Cada cliente tiene su propio `client_id` y su configuración (orígenes permitidos, duración de tokens, secreto si es confidencial).
- **Permission**: acción atómica dentro de una aplicación (p. ej. `invoice:read`, `invoice:approve`).
- **Role**: agrupación de permisos **dentro de una aplicación** (p. ej. `erp:ACCOUNTANT`).
- **UserApplicationAccess**: concesión de acceso de un usuario a una aplicación, con estado y vigencia opcional.
- **Asignación de roles**: roles de esa aplicación asignados al usuario dentro de su acceso.

**La propia plataforma es una aplicación.** El servicio se registra a sí mismo como la aplicación `auth-admin`, con roles como `SUPER_ADMIN` o `APP_MANAGER`. Así la administración de la plataforma usa el mismo modelo de autorización y no hacen falta "roles globales" especiales.

### 5.2 Diagrama entidad-relación

```mermaid
erDiagram
    USERS ||--o{ USER_APPLICATION_ACCESS : "tiene"
    APPLICATIONS ||--o{ USER_APPLICATION_ACCESS : "concede"
    APPLICATIONS ||--o{ APPLICATION_CLIENTS : "expone"
    APPLICATIONS ||--o{ ROLES : "define"
    APPLICATIONS ||--o{ PERMISSIONS : "define"
    ROLES ||--o{ ROLE_PERMISSIONS : ""
    PERMISSIONS ||--o{ ROLE_PERMISSIONS : ""
    USER_APPLICATION_ACCESS ||--o{ ACCESS_ROLES : "asigna"
    ROLES ||--o{ ACCESS_ROLES : ""
    USERS ||--o{ SESSIONS : "abre"
    APPLICATION_CLIENTS ||--o{ SESSIONS : "desde"
    SESSIONS ||--o{ REFRESH_TOKENS : "rota"
    USERS ||--o{ AUDIT_EVENTS : ""
```

### 5.3 Tablas

**Identidad**

```sql
users
  id                     UUID PK
  email                  CITEXT UNIQUE NOT NULL
  password_hash          VARCHAR NOT NULL
  first_name, last_name  VARCHAR
  status                 VARCHAR NOT NULL   -- ACTIVE | LOCKED | DISABLED
  email_verified_at      TIMESTAMPTZ NULL   -- reservado para la verificación de email (futuro)
  failed_login_attempts  INT NOT NULL DEFAULT 0
  locked_until           TIMESTAMPTZ NULL
  password_changed_at    TIMESTAMPTZ
  created_at, updated_at TIMESTAMPTZ
  version                BIGINT             -- bloqueo optimista
```

**Aplicaciones**

```sql
applications
  id                       UUID PK
  code                     VARCHAR UNIQUE NOT NULL  -- slug estable, p. ej. "erp"; se usa como `aud` del JWT
  name                     VARCHAR NOT NULL
  description              TEXT
  status                   VARCHAR NOT NULL          -- ACTIVE | INACTIVE
  self_registration        BOOLEAN NOT NULL DEFAULT FALSE  -- ¿se puede auto-registrar un usuario en esta app?
  default_role_id          UUID NULL FK roles        -- rol asignado al auto-registrarse
  created_at, updated_at, version

application_clients
  id                       UUID PK
  application_id           UUID FK applications
  client_id                VARCHAR UNIQUE NOT NULL   -- identificador público que envía el frontend
  name                     VARCHAR NOT NULL          -- p. ej. "ERP Web", "ERP Android"
  platform                 VARCHAR NOT NULL          -- WEB | MOBILE | SERVICE
  confidential             BOOLEAN NOT NULL          -- true solo para SERVICE
  client_secret_hash       VARCHAR NULL
  allowed_origins          TEXT[]                    -- CORS para clientes WEB
  access_token_ttl_seconds  INT NOT NULL              -- por defecto 900 (15 min); entre 1 min y 1 h
  refresh_token_ttl_seconds INT NOT NULL              -- por defecto 7 días (web) o 30 días (móvil); entre 1 h y 90 días
  status                   VARCHAR NOT NULL          -- ACTIVE | INACTIVE
  created_at, updated_at, version

permissions
  id                       UUID PK
  application_id           UUID FK applications
  code                     VARCHAR NOT NULL          -- "invoice:approve"
  description              TEXT
  UNIQUE (application_id, code)

roles
  id                       UUID PK
  application_id           UUID FK applications
  code                     VARCHAR NOT NULL          -- "ACCOUNTANT"
  name                     VARCHAR NOT NULL
  description              TEXT
  UNIQUE (application_id, code)

role_permissions
  role_id                  UUID FK roles
  permission_id            UUID FK permissions
  PRIMARY KEY (role_id, permission_id)
  -- regla: el rol y el permiso deben pertenecer a la misma aplicación
```

**Acceso**

```sql
user_application_access
  id                       UUID PK
  user_id                  UUID FK users
  application_id           UUID FK applications
  status                   VARCHAR NOT NULL          -- ACTIVE | SUSPENDED | REVOKED
  valid_from               TIMESTAMPTZ NOT NULL
  valid_until              TIMESTAMPTZ NULL          -- acceso temporal opcional
  granted_by               UUID NULL FK users
  created_at, updated_at, version
  UNIQUE (user_id, application_id)

access_roles
  access_id                UUID FK user_application_access
  role_id                  UUID FK roles
  PRIMARY KEY (access_id, role_id)
  -- regla: el rol debe pertenecer a la misma aplicación del acceso
```

**Sesiones y tokens**

```sql
sessions                                    -- una por login (dispositivo/navegador + cliente)
  id                       UUID PK
  user_id                  UUID FK users
  client_id                UUID FK application_clients
  ip_address               INET
  user_agent               VARCHAR
  device_name              VARCHAR NULL      -- informado por la app móvil
  created_at               TIMESTAMPTZ
  last_used_at             TIMESTAMPTZ
  revoked_at               TIMESTAMPTZ NULL
  revoked_reason           VARCHAR NULL      -- LOGOUT | LOGOUT_ALL | REUSE_DETECTED | ADMIN | ACCESS_REVOKED

refresh_tokens
  id                       UUID PK
  session_id               UUID FK sessions  -- la sesión es la "familia" de rotación
  token_hash               CHAR(64) UNIQUE   -- SHA-256; nunca se guarda el valor original
  expires_at               TIMESTAMPTZ
  used_at                  TIMESTAMPTZ NULL
  replaced_by              UUID NULL FK refresh_tokens
  created_at               TIMESTAMPTZ
```

**Auditoría**

```sql
audit_events
  id                       UUID PK
  type                     VARCHAR NOT NULL  -- LOGIN_SUCCESS, LOGIN_FAILED, ACCESS_DENIED, ACCESS_GRANTED,
                                             -- ROLE_ASSIGNED, PASSWORD_CHANGED, REFRESH_REUSE_DETECTED, ...
  actor_user_id            UUID NULL
  target_user_id           UUID NULL
  application_id           UUID NULL
  client_id                UUID NULL
  ip_address               INET
  metadata                 JSONB
  created_at               TIMESTAMPTZ
```

### 5.4 Reglas de negocio clave

1. Un usuario solo puede autenticarse en una aplicación si existe un acceso con estado `ACTIVE` dentro de su vigencia (`valid_from ≤ now < valid_until`), la aplicación y el cliente están `ACTIVE` y el usuario está `ACTIVE`.
2. Suspender o revocar un acceso **revoca las sesiones abiertas** de ese usuario en esa aplicación. Los access tokens ya emitidos siguen siendo válidos hasta que caducan (máximo 15 min); no se renuevan.
3. Los roles y permisos son siempre locales a una aplicación: un rol de `erp` nunca puede asignarse a un acceso de `crm`.
4. El auto-registro solo se permite en aplicaciones con `self_registration = true`, y asigna `default_role_id`. En el resto, el acceso lo concede un administrador.
5. Los usuarios son globales: una misma cuenta (email y contraseña) puede tener acceso a varias aplicaciones.

## 6. Estrategia de tokens

### 6.1 Access token (JWT RS256)

Se emite **por aplicación**: solo contiene la autorización de la aplicación del cliente que hizo el login.

```json
{
  "iss": "https://auth.example.com",
  "sub": "8f3c…-uuid-usuario",
  "aud": "erp",
  "azp": "erp-web",
  "sid": "uuid-sesion",
  "jti": "uuid-token",
  "iat": 1764259200,
  "exp": 1764260100,
  "email": "user@example.com",
  "roles": ["ACCOUNTANT"],
  "permissions": ["invoice:read", "invoice:approve"]
}
```

- `aud` es el `code` de la aplicación: cada backend valida que el token esté emitido para él.
- `azp` es el `client_id` que lo solicitó, y `sid` permite relacionar el token con la sesión.
- Duración por defecto: 15 minutos, configurable por cliente.
- Las claves RSA llevan `kid` para poder rotarlas y se publican en `/.well-known/jwks.json`.

### 6.2 Refresh token

- Valor opaco aleatorio de 256 bits, del que solo se persiste el hash SHA-256.
- **Rotación en cada uso con detección de reutilización**: si se presenta un token ya usado, se revoca la sesión completa (`REUSE_DETECTED`).
- Cada refresh vuelve a comprobar las reglas de acceso de la sección 5.4: si se ha revocado el acceso o cambiado los roles, el siguiente access token lo refleja.

### 6.3 Web y móvil

| Aspecto | Cliente `WEB` | Cliente `MOBILE` |
|---|---|---|
| Identificación | Header `X-Client-Id` | Header `X-Client-Id` |
| Access token | En el body de la respuesta, guardado en memoria | En el body de la respuesta |
| Refresh token | Cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` (**no** se devuelve en el body) | En el body, guardado en Keychain o Keystore |
| Envío del refresh token | Automático por la cookie | Body `{ "refreshToken": "…" }` |
| CSRF | Cookie `SameSite=Strict` + validación de `Origin` contra `allowed_origins` | No aplica |
| Duración del refresh token | 7 días (por defecto) | Configurable, p. ej. 30 días |

El comportamiento lo decide la **plataforma del cliente registrado**, no un parámetro que envíe el llamante, así que no se puede forzar el modo móvil desde un navegador.

### 6.4 Flujo de login

```mermaid
sequenceDiagram
    participant C as Cliente (web/móvil)
    participant A as Auth Service
    participant DB as PostgreSQL
    C->>A: POST /api/v1/auth/login {email, password} + X-Client-Id
    A->>DB: Buscar cliente → aplicación (ACTIVE)
    A->>DB: Buscar usuario, verificar contraseña (Argon2id)
    A->>DB: Verificar acceso usuario↔aplicación (ACTIVE y vigente)
    A->>DB: Cargar roles + permisos de esa aplicación
    A->>DB: Crear sesión + refresh token (hash)
    A-->>C: accessToken (JWT) + refreshToken (cookie o body según plataforma)
```

## 7. API REST (v1)

Todos los endpoints llevan el prefijo `/api/v1`. Los errores siguen **RFC 9457 (Problem Details)** con códigos propios (`AUTH_INVALID_CREDENTIALS`, `ACCESS_NOT_GRANTED`, `CLIENT_INACTIVE`, …).

### 7.1 Autenticación — módulo `auth`

| Método | Endpoint | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/register` | Auto-registro en una aplicación con `self_registration` | Pública + `X-Client-Id` |
| POST | `/auth/login` | Login en la aplicación del cliente | Pública + `X-Client-Id` (con rate limit) |
| POST | `/auth/refresh` | Rota el refresh token | Refresh token |
| POST | `/auth/logout` | Revoca la sesión actual | Bearer |
| POST | `/auth/logout-all` | Revoca todas las sesiones del usuario | Bearer |
| GET | `/.well-known/jwks.json` (sin prefijo) | Claves públicas | Pública |

### 7.2 Usuario autenticado — módulos `user` y `access`

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/users/me` | Perfil |
| PUT | `/users/me/password` | Cambio de contraseña (pide la actual; revoca las demás sesiones) |
| GET | `/users/me/applications` | Aplicaciones a las que tiene acceso, para un lanzador de apps |
| GET | `/users/me/sessions` | Sesiones o dispositivos activos |
| DELETE | `/users/me/sessions/{id}` | Cerrar una sesión concreta |

### 7.3 Administración — requiere roles de la aplicación `auth-admin`

| Método | Endpoint | Descripción |
|---|---|---|
| GET / POST | `/admin/applications` | Listar o crear aplicaciones |
| GET / PATCH | `/admin/applications/{appId}` | Detalle o actualización (incluye activar/desactivar) |
| GET / POST | `/admin/applications/{appId}/clients` | Clientes de la aplicación |
| PATCH | `/admin/applications/{appId}/clients/{clientId}` | Actualizar cliente (orígenes, TTL, estado) |
| POST | `/admin/applications/{appId}/clients/{clientId}/secret` | Regenerar el secreto (clientes `SERVICE`) |
| GET / POST | `/admin/applications/{appId}/permissions` | Catálogo de permisos |
| GET / POST | `/admin/applications/{appId}/roles` | Roles de la aplicación |
| PUT | `/admin/applications/{appId}/roles/{roleId}/permissions` | Definir los permisos de un rol |
| GET / POST | `/admin/users` | Listar (paginado) o crear usuarios |
| PATCH | `/admin/users/{userId}/status` | Bloquear, activar o deshabilitar |
| GET | `/admin/users/{userId}/access` | Accesos del usuario |
| PUT | `/admin/applications/{appId}/access/{userId}` | Conceder o actualizar el acceso (estado, vigencia, roles) |
| DELETE | `/admin/applications/{appId}/access/{userId}` | Revocar el acceso y sus sesiones |
| GET | `/admin/applications/{appId}/access` | Usuarios con acceso a la aplicación |
| GET | `/admin/audit-events` | Consulta de auditoría (filtros por usuario, aplicación, tipo y fechas) |

**Permisos de `auth-admin`**

| Rol | Alcance |
|---|---|
| `SUPER_ADMIN` | Todo |
| `APP_MANAGER` | Gestiona aplicaciones, clientes, roles y permisos |
| `ACCESS_MANAGER` | Gestiona usuarios y concesiones de acceso |
| `AUDITOR` | Solo lectura de la auditoría |

En el arranque inicial (migración + `ApplicationRunner`) se crean la aplicación `auth-admin`, su cliente web, sus roles y un usuario `SUPER_ADMIN` cuyas credenciales se toman de variables de entorno.

## 8. Seguridad

- **Anti-enumeración**: respuestas genéricas y tiempos homogéneos en login y registro.
- **Fuerza bruta**: rate limiting por IP, email y cliente (Bucket4j), y bloqueo progresivo de la cuenta (`failed_login_attempts`, `locked_until`).
- **Contraseñas** (NIST 800-63B): mínimo 12 caracteres, máximo 128, comprobación contra una lista de contraseñas comunes y sin reglas de composición arbitrarias.
- **CORS dinámico**: los orígenes permitidos se toman de `allowed_origins` de los clientes `WEB` activos.
- **Cabeceras de seguridad**: HSTS, `X-Content-Type-Options`, `Referrer-Policy`; sin stack traces en las respuestas.
- **Secretos** fuera del repositorio: variables de entorno y perfiles `local`, `test` y `prod`.
- **Auditoría** de todos los eventos sensibles (sección 5.3).
- **Principio de mínimo privilegio**: los tokens solo llevan los permisos de la aplicación solicitada.

## 9. Testing y calidad

- **Unitarios**: dominio y casos de uso, sin contexto de Spring.
- **Integración**: `@SpringBootTest` + Testcontainers (PostgreSQL) + MockMvc, con flujos completos:
  - login en una app con acceso → 200; en una app sin acceso → 403 `ACCESS_NOT_GRANTED`;
  - revocación del acceso → el refresh falla;
  - reutilización de un refresh token → la sesión queda revocada;
  - cliente web recibe la cookie y el cliente móvil recibe el token en el body.
- **Arquitectura**: ArchUnit (sección 4).
- **Cobertura**: JaCoCo ≥ 80 %, verificada en CI.

## 10. Plan de entrega

| Fase | Alcance |
|---|---|
| **0. Base** | Esqueleto Maven + Spring Boot 4, Docker Compose, Flyway, CI, manejo global de errores, OpenAPI, ArchUnit |
| **1. Usuarios y aplicaciones** | Módulos `user` y `apps`: entidades, migraciones, CRUD de administración, seed de `auth-admin`. Los endpoints de administración quedan **sin proteger hasta la fase 3** |
| **2. Acceso** | Módulo `access`: concesiones, vigencia, asignación de roles, `/users/me/applications` |
| **3. Autenticación** | Login por cliente, JWT RS256 con roles y permisos, JWKS, `/users/me`, autorización de endpoints de administración |
| **4. Sesiones** | Refresh con rotación y detección de reutilización, cookie (web) o body (móvil), logout, logout-all, gestión de sesiones |
| **5. Endurecimiento** | Rate limiting, bloqueo de cuenta, auditoría completa, cambio de contraseña |
| **Futuro** | Verificación de email, recuperación de contraseña por email, MFA (TOTP), login social, SSO entre aplicaciones / migración a Spring Authorization Server, token exchange para clientes `SERVICE` |

> La **recuperación de contraseña por email** pasa también a versiones futuras, porque necesita la misma infraestructura de envío de correo que la verificación de email. En v1 un administrador puede restablecer la contraseña de un usuario.

## 11. Convenciones de trabajo

- Rama de integración: **`developer`**. Las funcionalidades se desarrollan en `feature/<fase>-<descripcion>` y se integran en `developer` mediante PR.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- Paquete base: `com.henryfll.auth`.
