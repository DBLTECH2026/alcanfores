# syntax=docker/dockerfile:1.7
# Imagen de CUALQUIER microservicio de Alcanfores. La etapa de construcción es idéntica para todos
# (compila el reactor Maven completo una sola vez y BuildKit la reutiliza); la etapa final copia solo el JAR
# del módulo indicado en el argumento MODULO.
#   docker build --build-arg MODULO=reservas-service -t alcanfores/reservas-service backend/

FROM maven:3.9-eclipse-temurin-17 AS construccion
WORKDIR /fuente
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre
ARG MODULO
LABEL org.opencontainers.image.source="https://github.com/DBLTECH2026/alcanfores" \
      org.opencontainers.image.description="Alcanfores · ${MODULO}"
# Fuentes para PDFs/imagenes generadas en el servidor y curl para el healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --home /app alcanfores \
    && mkdir -p /app/uploads && chown -R alcanfores /app
WORKDIR /app
COPY --from=construccion /fuente/${MODULO}/target/${MODULO}-1.0.0.jar /app/app.jar
USER alcanfores
ENV TZ=America/Lima \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Duser.timezone=America/Lima -Dfile.encoding=UTF-8"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
