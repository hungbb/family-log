# syntax=docker/dockerfile:1.7
#
# Gia Ký API — production image.
#
# Tests are not run here: the pipeline runs `./mvnw verify` (unit + Testcontainers ITs) before it
# builds this image, and Testcontainers cannot start a database from inside `docker build`.

# --- Build ------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first, so a source-only change reuses the cached layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/api-*.jar /src/app.jar \
    && java -Djarmode=tools -jar /src/app.jar extract --layers --launcher --destination /src/extracted

# --- Runtime ----------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 giaky \
    && useradd --system --uid 10001 --gid giaky --home-dir /app --shell /usr/sbin/nologin giaky \
    && mkdir -p /app /data/media \
    && chown -R giaky:giaky /app /data/media

WORKDIR /app

# Layered: dependencies change far less often than application classes.
COPY --from=build --chown=giaky:giaky /src/extracted/dependencies/ ./
COPY --from=build --chown=giaky:giaky /src/extracted/spring-boot-loader/ ./
COPY --from=build --chown=giaky:giaky /src/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=giaky:giaky /src/extracted/application/ ./

USER giaky

ENV SPRING_PROFILES_ACTIVE=prod \
    MEDIA_LOCAL_DIR=/data/media \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

VOLUME ["/data/media"]
EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
