# syntax=docker/dockerfile:1
# Image du backend (ADR-005) : construction Gradle puis exécution sur JRE 21, utilisateur non-root.
# Les tests ne sont pas exécutés ici : ils tournent en CI (Testcontainers nécessite Docker).

# ---- Étape 1 : construction ----------------------------------------------------
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

# Résolution des dépendances dans une couche séparée (cache tant que le build ne change pas)
COPY settings.gradle build.gradle ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml
RUN gradle dependencies --no-daemon --quiet > /dev/null

COPY src src
RUN gradle bootJar --no-daemon --quiet \
 && java -Djarmode=tools -jar build/libs/bibliotheque-backend.jar extract --layers --launcher --destination build/extracted

# ---- Étape 2 : exécution -------------------------------------------------------
FROM eclipse-temurin:21-jre-noble

# curl : sonde de santé du conteneur ; utilisateur système sans shell de connexion
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
# Couches Spring Boot, de la moins à la plus changeante
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./

USER app
EXPOSE 8080
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
