# Guía de despliegue

Cómo ejecutar el servicio de autenticación en producción. El diseño está en [ARCHITECTURE.md](ARCHITECTURE.md) y la configuración completa de desarrollo en el [README](../README.md).

## Resumen

El servicio es **una imagen de contenedor sin estado** más una base de datos **PostgreSQL 17**. Las sesiones, los refresh tokens y la auditoría viven en la base de datos, así que se puede reiniciar o escalar sin perder sesiones.

| Elemento | Detalle |
|---|---|
| Imagen | Multi-etapa (`Dockerfile`): compila con JDK 25 y ejecuta con JRE 25. Usuario sin privilegios (UID 10001), capas separadas para dependencias y código |
| Puerto | `8080` (HTTP). **El TLS lo termina un proxy inverso** delante |
| Base de datos | PostgreSQL 17. Las migraciones (Flyway) se aplican solas al arrancar |
| Arranque | Unos 7 segundos en las pruebas de este repositorio |
| Logs | JSON (formato ECS) por la salida estándar |

## Puesta en marcha con Docker Compose

`compose.prod.yaml` es un despliegue de referencia con la aplicación y PostgreSQL. Sirve tal cual en un único servidor, o como base para adaptarlo a otro orquestador.

```bash
cp .env.example .env        # y completar los valores (ver más abajo)
docker build -t authentication-app:latest .
docker compose -f compose.prod.yaml up -d
```

Comprobar que está sano:

```bash
curl -s http://localhost:8080/actuator/health/readiness
```

### Valores obligatorios

El perfil `prod` **no tiene valores por defecto** para lo que identifica al despliegue. Si falta alguno, la aplicación no arranca, en lugar de usar los de desarrollo.

| Variable | Descripción |
|---|---|
| `POSTGRES_PASSWORD` | Contraseña de la base de datos (en `compose.prod.yaml`; la aplicación la recibe como `DB_PASSWORD`) |
| `JWT_ISSUER` | URL pública del servicio, p. ej. `https://auth.example.com`. Va en el claim `iss` de los tokens y la validan las aplicaciones que los consumen |
| `JWT_PRIVATE_KEY` | Clave privada RSA (≥ 2048 bits) en PEM PKCS#8 |

Fuera de Compose, la aplicación lee directamente `DB_URL` (JDBC, p. ej. `jdbc:postgresql://host:5432/auth`), `DB_USERNAME` y `DB_PASSWORD`.

### Valores opcionales

| Variable | Por defecto | Descripción |
|---|---|---|
| `JWT_KEY_ID` | `auth-key-1` | `kid` de la clave, publicado en `/.well-known/jwks.json`. Cambiarlo al cambiar la clave |
| `AUTH_BOOTSTRAP_ADMIN_EMAIL` / `AUTH_BOOTSTRAP_ADMIN_PASSWORD` | vacío | Crea el primer `SUPER_ADMIN` (ver [Primer administrador](#primer-administrador)) |
| `OPENAPI_ENABLED` | `false` | Expone Swagger UI y `/v3/api-docs` |
| `DB_POOL_SIZE` | `10` | Tamaño del pool de conexiones |
| `FORWARD_HEADERS_STRATEGY` | `native` | Cómo se interpretan `X-Forwarded-*` (ver [Proxy inverso](#proxy-inverso-y-tls)) |
| `APP_SECURITY_RATE_LIMIT_*`, `APP_SECURITY_LOCKOUT_*` | ver [README](../README.md) | Límites de intentos y bloqueo de cuentas |

## Clave de firma de los tokens

Los access tokens se firman con RS256. La clave privada **solo la conoce este servicio**; las demás aplicaciones validan con la clave pública de `/.well-known/jwks.json`.

```bash
# 1. Generar la clave (guardarla en un gestor de secretos; no subirla al repositorio)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem

# 2. Convertirla a una línea con los saltos como \n, lista para poner en .env
awk 'NF {printf "%s\\n", $0}' jwt-private.pem
```

Debe estar en formato **PKCS#8** (`BEGIN PRIVATE KEY`). Si empieza por `BEGIN RSA PRIVATE KEY` es PKCS#1: conviértela con `openssl pkcs8 -topk8 -nocrypt -in clave.pem`.

**Si se pierde la clave**, hay que generar otra, y todos los access tokens vigentes dejan de ser válidos (ver más abajo).

### Cambiar la clave

En esta versión hay **una sola clave de firma** publicada. Cambiarla (`JWT_PRIVATE_KEY` y `JWT_KEY_ID`) y reiniciar tiene este efecto:

- Los access tokens ya emitidos (15 minutos de vida por defecto) dejan de ser válidos en cuanto la aplicación o los consumidores obtienen el nuevo JWKS. Los clientes lo notan como un `401`.
- Los **refresh tokens no se ven afectados** (son valores opacos guardados en la base de datos), así que los clientes se recuperan renovando el token.
- El JWKS se puede cachear 15 minutos, por lo que algunos consumidores pueden tardar ese tiempo en ver la clave nueva.

Conviene cambiarla en una ventana de poco tráfico. La rotación sin cortes, con varias claves publicadas a la vez, queda para una versión futura.

## Primer administrador

Sin ningún usuario no se puede entrar a la administración. Define estas variables **en el primer arranque**:

```
AUTH_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
AUTH_BOOTSTRAP_ADMIN_PASSWORD=una contraseña larga y aleatoria (mínimo 12 caracteres)
```

- Crea el usuario con el rol `SUPER_ADMIN` en la aplicación `auth-admin`. Si el usuario ya existe, no cambia su contraseña.
- La contraseña debe cumplir la política (12 a 128 caracteres, sin ser común ni contener el email); si no, la aplicación no arranca y el error nombra las variables.
- **Una vez creado, quita la variable de la contraseña** de la configuración y cámbiala desde la API (`PUT /api/v1/users/me/password`).

### Registrar la primera aplicación

Con el administrador se registra cada aplicación que usará el servicio. Ejemplo con `curl`:

```bash
BASE=https://auth.example.com
TOKEN=$(curl -s -X POST $BASE/api/v1/auth/login -H 'Content-Type: application/json' \
  -H 'X-Client-Id: auth-admin-web' \
  -d '{"email":"admin@example.com","password":"..."}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
AUTH="Authorization: Bearer $TOKEN"

# Aplicación, cliente web y rol
APP=$(curl -s -X POST $BASE/api/v1/admin/applications -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"code":"erp","name":"ERP"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
curl -s -X POST $BASE/api/v1/admin/applications/$APP/clients -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"ERP Web","platform":"WEB","allowedOrigins":["https://erp.example.com"]}'
curl -s -X POST $BASE/api/v1/admin/applications/$APP/roles -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"code":"USER","name":"Usuario"}'
```

La respuesta al crear el cliente incluye su `clientId`, que es el valor que enviará el frontend en la cabecera `X-Client-Id`. Los orígenes de `allowedOrigins` son los únicos que podrán llamar a la API desde un navegador (CORS).

Para que un frontend web pueda usar la cookie del refresh token, el frontend y esta API deben servirse bajo el **mismo sitio** (por ejemplo `erp.example.com` y `auth.example.com`), porque la cookie es `SameSite=Strict`.

## Proxy inverso y TLS

La aplicación habla HTTP en el puerto 8080. **Pon un proxy inverso con TLS delante** (nginx, Caddy, un balanceador de la nube o un Ingress) y no expongas el puerto 8080 a Internet.

- **TLS es obligatorio:** la cookie del refresh token lleva el atributo `Secure`, que los navegadores solo aceptan sobre HTTPS.
- **El proxy debe enviar** `X-Forwarded-For` y `X-Forwarded-Proto`. Con `FORWARD_HEADERS_STRATEGY=native` (el valor por defecto en producción), la aplicación solo hace caso a esas cabeceras si la petición llega **desde un proxy de confianza**, que por defecto son las direcciones de redes privadas. Así, un cliente externo no puede falsificar su IP.
  - Si tu proxy tiene una IP pública, o está fuera de los rangos privados, indícalo con `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (expresión regular de IPs).
  - Con `FORWARD_HEADERS_STRATEGY=none` se ignoran estas cabeceras.
- **Por qué importa la IP:** los límites de intentos de login y refresh y la auditoría usan la IP del cliente. Si el proxy no la reenvía, todos los clientes parecerán tener la IP del proxy y compartirán el mismo límite.
- Configura **HSTS** y la redirección de HTTP a HTTPS en el proxy.

Ejemplo mínimo para nginx:

```nginx
location / {
    proxy_pass         http://127.0.0.1:8080;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Forwarded-For   $remote_addr;
    proxy_set_header   X-Forwarded-Proto $scheme;
}
```

## Salud y observabilidad

| Endpoint | Uso |
|---|---|
| `/actuator/health/liveness` | ¿El proceso está vivo? (reiniciar si falla) |
| `/actuator/health/readiness` | ¿Puede atender tráfico? (sacar del balanceador si falla). La imagen lo usa en su `HEALTHCHECK` |
| `/actuator/health` | Estado general |

- Los endpoints de Actuator expuestos son solo `health` e `info`, sin detalles.
- **Logs:** JSON por la salida estándar, listo para recogerlos con tu sistema de logs. Los eventos de seguridad (login, bloqueo, reutilización de refresh tokens…) están además en la tabla de auditoría: `GET /api/v1/admin/audit-events`.
- **Métricas:** esta versión no exporta métricas a un sistema externo (Prometheus, etc.).
- **Apagado:** al recibir `SIGTERM` la aplicación termina las peticiones en curso (hasta 30 s). Configura el orquestador con un plazo algo mayor (`compose.prod.yaml` usa 40 s).

## Base de datos

- **Migraciones:** se aplican al arrancar. Flyway usa un bloqueo en la base de datos, así que varias réplicas arrancando a la vez no se pisan.
- **Las migraciones solo van hacia adelante.** Para volver a una versión anterior de la aplicación después de una migración hay que **restaurar una copia de la base de datos**. Haz una copia antes de cada actualización.
- **Copias de seguridad:** la base de datos contiene usuarios, hashes de contraseñas (Argon2id), sesiones y la auditoría. Haz copias periódicas y pruébalas restaurando. No incluyen la clave de firma, que se guarda aparte.
- **Usuario de la base de datos:** debe poder crear y modificar tablas (por las migraciones). Para endurecerlo, se pueden ejecutar las migraciones con un usuario y la aplicación con otro, pero esta versión usa el mismo para ambos.
- `compose.prod.yaml` **no publica el puerto de PostgreSQL**: solo es accesible desde la aplicación.

## Varias réplicas

El servicio no guarda estado en memoria que haya que compartir, con una excepción:

- **Los límites de intentos son por instancia.** Con N réplicas, el límite efectivo de cada IP o email es hasta N veces mayor. El bloqueo progresivo de cuentas **sí** es compartido (se guarda en la base de datos). Un límite compartido (Redis) queda para una versión futura.

## Actualizar a una versión nueva

1. Haz una copia de la base de datos.
2. Construye o descarga la imagen nueva.
3. `docker compose -f compose.prod.yaml up -d` recrea solo la aplicación; las migraciones se aplican al arrancar.
4. Comprueba `/actuator/health/readiness` y revisa los logs.

Con varias réplicas, no se ha probado ejecutar dos versiones distintas a la vez. Si una versión incluye una migración que no es compatible con la anterior, actualiza todas las réplicas a la vez.

## Lista de comprobación antes de abrir al público

- [ ] TLS en el proxy, con redirección de HTTP a HTTPS y HSTS.
- [ ] `JWT_ISSUER` es la URL pública real.
- [ ] La clave de firma está en un gestor de secretos, con copia de seguridad.
- [ ] Contraseña de la base de datos larga y aleatoria; el puerto de PostgreSQL no es accesible desde fuera.
- [ ] El primer administrador está creado y la variable de su contraseña se ha quitado de la configuración.
- [ ] El proxy envía `X-Forwarded-For` y `X-Forwarded-Proto`, y la IP del proxy está en los rangos de confianza.
- [ ] Los clientes web tienen `allowedOrigins` con los orígenes reales (y solo esos).
- [ ] Hay copias de seguridad de la base de datos y se ha probado a restaurarlas.
- [ ] `OPENAPI_ENABLED` sigue en `false` (o está protegido de otra forma).
- [ ] Se ha ejecutado la prueba de humo (`scripts/smoke-test.sh`) con la imagen que se va a desplegar.

## Prueba de humo

`scripts/smoke-test.sh` levanta `compose.prod.yaml` con claves y contraseñas efímeras (sin tocar tu `.env`) y comprueba que la imagen arranca con el perfil `prod` y se comporta bien:

```bash
docker build -t authentication-app:latest .
scripts/smoke-test.sh
```

Comprueba, entre otras cosas: salud, `401` sin token, Swagger desactivado, JWKS sin material privado, login del administrador inicial, atributos de la cookie del refresh token, claims del token, IP del cliente tras un proxy, que no se ejecuta como root y que el sistema de ficheros es de solo lectura. También se ejecuta en CI en cada cambio.

## Problemas frecuentes al arrancar

La aplicación sale con código 1 y el motivo está en el log.

| Mensaje | Causa |
|---|---|
| `JWT_PRIVATE_KEY is required` | Falta la clave de firma |
| `JWT_PRIVATE_KEY must be PKCS#8` | La clave está en formato PKCS#1 (`BEGIN RSA PRIVATE KEY`) |
| `JWT_PRIVATE_KEY is not a valid RSA PKCS#8 private key` | La clave está truncada o con los saltos de línea mal puestos. Usa el formato de una línea con `\n` |
| `JWT_ISSUER is required` | Falta `JWT_ISSUER` |
| `'url' must start with "jdbc"` | Falta `DB_URL` |
| Error de conexión o autenticación con la base de datos | `DB_URL`, `DB_USERNAME` o `DB_PASSWORD` incorrectos, o la base de datos no es accesible |
| `Invalid bootstrap admin configuration` | La contraseña del administrador inicial no cumple la política |
