# authentication-app

Servicio centralizado de autenticación y control de acceso a aplicaciones.

- **Stack:** Java 25 · Spring Boot 4.0 · Maven · PostgreSQL 17 · Flyway
- **Arquitectura:** hexagonal, por módulos (`user`, `apps`, `access`, `auth`). Detalle en [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Requisitos

- JDK 25
- Docker (para PostgreSQL en local y para los tests con Testcontainers)

## Ejecutar en local

```bash
./mvnw spring-boot:run
```

Con el perfil `local` (el predeterminado), Spring Boot levanta PostgreSQL automáticamente a partir de `compose.yaml`.

| URL | Descripción |
|---|---|
| http://localhost:8080/actuator/health | Estado del servicio |
| http://localhost:8080/swagger-ui.html | Documentación de la API |
| http://localhost:8080/v3/api-docs | Especificación OpenAPI (JSON) |

## Tests y calidad

```bash
./mvnw verify
```

Ejecuta los tests unitarios, los de integración (PostgreSQL real con Testcontainers) y las reglas de arquitectura (ArchUnit), comprueba el formato (Spotless) y exige una cobertura de líneas ≥ 80 % (JaCoCo; informe en `target/site/jacoco/index.html`).

Para formatear el código antes de hacer commit:

```bash
./mvnw spotless:apply
```

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/auth` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | `auth` | Usuario de la base de datos |
| `DB_PASSWORD` | `auth` | Contraseña de la base de datos |
| `PORT` | `8080` | Puerto HTTP |
| `SPRING_PROFILES_ACTIVE` | `local` | `local`, `test` o `prod` |
| `OPENAPI_ENABLED` | `false` (solo en `prod`) | Expone Swagger UI en producción |
| `AUTH_BOOTSTRAP_ADMIN_EMAIL` | _(vacío)_ | Email del primer `SUPER_ADMIN` de `auth-admin`. Vacío: no se crea |
| `AUTH_BOOTSTRAP_ADMIN_PASSWORD` | _(vacío)_ | Su contraseña (mínimo 12 caracteres, sin contener el email) |
| `JWT_PRIVATE_KEY` | _(vacío)_ | Clave privada RSA (≥ 2048 bits, PEM PKCS#8) para firmar los tokens. **Obligatoria en `prod`**. En `local` y `test`, si falta, se genera una clave temporal |
| `JWT_KEY_ID` | `auth-key-1` | `kid` de la clave, publicado en `/.well-known/jwks.json` |
| `JWT_ISSUER` | `http://localhost:8080` | Valor del claim `iss` |
| `APP_SECURITY_REFRESH_COOKIE_SECURE` | `true` (`false` en `local`) | Atributo `Secure` de la cookie del refresh token. Solo debe desactivarse en desarrollo con HTTP |
| `APP_SECURITY_RATE_LIMIT_LOGIN_BY_IP_CAPACITY` | `30` | Intentos de login por IP y minuto. Hay variables equivalentes para `LOGIN_BY_EMAIL` (`10`) y `REFRESH_BY_IP` (`120`); `0` desactiva el límite |
| `APP_SECURITY_LOCKOUT_MAX_FAILED_ATTEMPTS` | `5` | Fallos antes de bloquear la cuenta temporalmente. El bloqueo empieza en `APP_SECURITY_LOCKOUT_BASE_LOCK_DURATION` (`15m`) y se dobla con cada fallo hasta `APP_SECURITY_LOCKOUT_MAX_LOCK_DURATION` (`24h`) |

El administrador inicial se crea al arrancar solo si no existe. Si ya existe, no se cambia su contraseña: solo se le concede el acceso a `auth-admin` si no lo tiene.

Para generar una clave de firma:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
```

## Uso de la API

Login desde un cliente registrado. El primer administrador entra con el cliente `auth-admin-web`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -H 'X-Client-Id: auth-admin-web' -d '{"email": "admin@example.com", "password": "..."}'
```

La respuesta incluye `accessToken`, un JWT que se envía como `Authorization: Bearer <token>`. Lleva los roles y permisos del usuario en la aplicación del cliente. Las demás aplicaciones validan estos tokens con la clave pública de `/.well-known/jwks.json`.

El login también abre una sesión y entrega un refresh token. Cómo se entrega depende del tipo de cliente:

| Cliente | Dónde llega el refresh token | Cómo renovar (`POST /api/v1/auth/refresh` con `X-Client-Id`) |
|---|---|---|
| `WEB` | Cookie `refresh_token` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`) | El navegador envía la cookie (`credentials: 'include'`) |
| `MOBILE` | Campo `refreshToken` del body | Body `{"refreshToken": "..."}`; guardarlo en Keychain o Keystore |

Cada refresh token sirve una sola vez. Si se reutiliza, se cierra la sesión completa. Para cerrar sesión se usa `POST /api/v1/auth/logout` (la sesión actual) o `/logout-all` (todas), y `GET /api/v1/users/me/sessions` lista los dispositivos con sesión abierta.

El usuario cambia su contraseña con `PUT /api/v1/users/me/password` (`currentPassword`, `newPassword`), y un administrador puede restablecerla con `PUT /api/v1/admin/users/{id}/password`. Los eventos de seguridad se consultan en `GET /api/v1/admin/audit-events` (permiso `audit:read`).

## Despliegue

El servicio se despliega como una imagen de contenedor más PostgreSQL:

```bash
cp .env.example .env        # completar los valores
docker build -t authentication-app:latest .
docker compose -f compose.prod.yaml up -d
```

La guía completa (configuración obligatoria, clave de firma, primer administrador, proxy inverso y TLS, copias de seguridad y lista de comprobación) está en [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md). `scripts/smoke-test.sh` verifica una imagen de extremo a extremo.

## Flujo de trabajo

- `developer` es la rama de integración.
- Cada funcionalidad se desarrolla en `feature/<fase>-<descripcion>` y se integra en `developer` mediante PR.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/).
