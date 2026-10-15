# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY . /app
RUN sed -i 's/\r$//' mvnw && \
    chmod +x mvnw && \
    ./mvnw -q -DskipTests package && \
    mkdir -p volumes
# All configuration comes from the environment (see .env.example); a fresh
# volumes/ directory gets its schema and seed data on first boot.
CMD ["java", "-jar", "target/spring-0.0.1-SNAPSHOT.jar"]
EXPOSE 8585
EXPOSE 8589
