# OCS Kit Spring backend. Build on a JDK, run the jar on a JRE as a non-root user.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Dependencies first for layer caching; tests are neither compiled nor run in the image build
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && \
    ./mvnw -B -q -Dmaven.test.skip=true dependency:go-offline
COPY src src
RUN ./mvnw -B -q -Dmaven.test.skip=true package && \
    cp target/spring-*-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
# volumes/ holds everything the app writes (SQLite, backups, uploads); usually a bind mount
RUN adduser -D -u 1000 -s /sbin/nologin appuser && \
    mkdir -p /app/volumes && \
    chown -R appuser:appuser /app/volumes
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
# All configuration comes from the environment (see .env.example); a fresh
# volumes/ directory gets its schema and seed data on first boot.
ENV HOME=/tmp \
    CODE_RUNNER=remote \
    UPLOAD_DIR=volumes/uploads/
USER 1000:1000
EXPOSE 8585 8589
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8585/login || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
