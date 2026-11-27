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

## Flujo de trabajo

- `developer` es la rama de integración.
- Cada funcionalidad se desarrolla en `feature/<fase>-<descripcion>` y se integra en `developer` mediante PR.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/).
