# syntax=docker/dockerfile:1

# ---- Compilación -----------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Primero lo que cambia poco (wrapper y dependencias), para aprovechar la caché de capas.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Los tests y la cobertura se ejecutan en CI (`./mvnw verify`); aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && cp target/authentication-app-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Ejecución -------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# Usuario sin privilegios (UID fijo, para poder fijar permisos en volúmenes y en Kubernetes).
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# Capas de menor a mayor frecuencia de cambio: las dependencias se reutilizan entre versiones.
COPY --from=build --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# La imagen no incluye curl ni wget (menos superficie de ataque): la sonda usa bash y /dev/tcp.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n1 <&3 | grep -q " 200"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
