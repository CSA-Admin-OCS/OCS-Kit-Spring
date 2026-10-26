# AGENTS.md

Guidance for maintainers (human or AI) of the OCS-in-a-Kit Spring backend. This repo is a
school-neutral copy of an upstream classroom project and ships as a Docker image.

## Rules

- No school-specific values in code: names, domains, emails, URLs and secrets come from
  configuration. Add a `${ENV_VAR:default}` line to `application.properties` (or read the env
  var) and document it in `.env.example`. Secrets never get a default.
- Before committing, this must return nothing (except this line and `LICENSE`):
  `grep -rniE 'DNHS|del norte|powayusd|nighthawk|jm1021|mortensen|AIza|opencodingsociety' --exclude-dir=.git --exclude-dir=target .`
- A fresh container with an empty `volumes/` must boot, create its schema and seed. Do not
  add startup code that assumes tables already exist.

## Commands

- `./mvnw -DskipTests package` builds `target/spring-0.0.1-SNAPSHOT.jar` (Java 21).
  Without a local JDK 21: `docker run --rm -v "$PWD":/app -w /app -v $HOME/.m2:/root/.m2 eclipse-temurin:21-jdk ./mvnw -q -DskipTests package`
- `./mvnw test` runs the JUnit 5 + Mockito unit tests (no Spring context tests).
- `docker compose up -d --build` runs the app (8585, 8589) and the code runner, mounting `./volumes`.
- Smoke test: `curl localhost:8585/api/jokes/`, then `POST /authenticate` with
  `{"uid":"admin","password":"<ADMIN_PASSWORD>"}` and check for the `jwt_java_spring` cookie.

## Configuration

- `application.properties` holds defaults; `.env` (gitignored) and real environment variables
  override them. `kit.*` properties carry the school settings (`SCHOOL_NAME`, `FRONTEND_URL`,
  `CORS_ORIGINS`, `COOKIE_DOMAIN`, `ALLOWED_EMAIL_DOMAINS`, `ADMIN_EMAIL`, `KIT_DEV_MODE`).
- `system/KitEnvironmentPostProcessor` (registered in `META-INF/spring.factories`) fails
  startup without a valid `JWT_SECRET` (unless `KIT_DEV_MODE=true`) and switches
  `ddl-auto` to `update` when the SQLite file does not exist yet.
- Seed accounts come from `Person.init()`; `ADMIN_PASSWORD`, `TEACHER_PASSWORD` and
  `DEFAULT_PASSWORD` are required, with no fallback.

## Architecture

- `system/`: entry point, startup config, `ModelInit` seeding, standalone DB tools
  (`DatabaseInitializer`, `DatabaseMigrator`).
- `security/`: cookie-based JWT. `POST /authenticate` sets the httpOnly `jwt_java_spring`
  cookie; `JwtRequestFilter` reads it on `/api/**`. `SecurityConfig` holds the endpoint access
  policy and CORS; `CookieFactory` builds every auth cookie.
- `mvc/<feature>/`: one package per feature, following JPA `@Entity` + `*JpaRepository` +
  `*ApiController` (REST under `/api`), with an optional `*ViewController` for Thymeleaf pages.
- Hibernate uses globally quoted identifiers, so reserved words such as `groups` work as names.
- Two connectors: 8585 (HTTP) and 8589 (group-chat STOMP websocket). See
  `deploy/nginx.conf.template`.
- Course groups and bell periods are configured in `courses.*` in `application.properties`.
