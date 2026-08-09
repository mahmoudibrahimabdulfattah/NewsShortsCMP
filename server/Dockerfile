# Build stage — only the :server module, so the image never needs the Android SDK.
FROM gradle:8.14-jdk17 AS build
WORKDIR /build
COPY gradle/libs.versions.toml gradle/libs.versions.toml
COPY server/deploy/settings.gradle.kts settings.gradle.kts
COPY server server
RUN gradle :server:installDist --no-daemon

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/server/build/install/server /app

# SQLite holds only RSS-derived data, so an ephemeral disk is fine —
# a restart rebuilds it on the first ingestion cycle.
ENV DB_PATH=/tmp/news.db
# Fits the 512 MB free-tier container.
ENV JAVA_OPTS="-Xmx384m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

EXPOSE 8080
CMD ["./bin/server"]
