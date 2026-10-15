# OCS Kit: Spring backend

The Spring Boot (Java 21) backend of OCS-in-a-Kit. It stores and serves data for the kit's
Pages site through REST APIs under `/api/**`, handles JWT login, and ships a small admin UI
(Thymeleaf) for managing users and data.

## Install with Docker (recommended)

You need Docker with the compose plugin.

1. Copy the example configuration and fill it in:

   ```bash
   cp .env.example .env
   ```

   At minimum set:

   | Variable | What it is |
   |---|---|
   | `JWT_SECRET` | Signs login tokens. Generate with `openssl rand -base64 48`. |
   | `ADMIN_PASSWORD`, `TEACHER_PASSWORD`, `DEFAULT_PASSWORD` | Passwords for the seeded accounts. 8+ characters with upper, lower, digit and symbol. |
   | `ADMIN_EMAIL` | Contact email; also owns the seeded hall-pass queues. |
   | `SCHOOL_NAME`, `FRONTEND_URL` | Your school's name and the URL of your Pages site. |
   | `CORS_ORIGINS` | Comma-separated origins allowed to call the API (your Pages site). |
   | `COOKIE_DOMAIN` | e.g. `.example.org` when Pages and this API share a parent domain; empty otherwise. |

   Every other variable is optional and documented in `.env.example`. Features that need an
   external service (Gemini, email, S3, ...) turn themselves off when their keys are empty.

2. Start it:

   ```bash
   docker compose up -d --build
   ```

   On the first start the app finds no database in `./volumes`, creates the schema and seeds
   the admin, teacher and sample accounts. Later starts reuse the same database.

3. Check it:

   ```bash
   curl http://localhost:8585/api/jokes/
   curl -i -X POST http://localhost:8585/authenticate \
        -H 'Content-Type: application/json' \
        -d '{"uid":"admin","password":"<ADMIN_PASSWORD>"}'
   ```

   The admin UI is at http://localhost:8585/ (log in with the admin account).

The app refuses to start without `JWT_SECRET`, and the first boot refuses to seed without
the three passwords. Error messages name the missing variable.

### Local HTTP

Browsers drop `Secure` / `SameSite=None` cookies on plain `http://`. When testing on
`localhost` without TLS, uncomment the `jwt.cookie.*` and `server.servlet.session.cookie.*`
lines at the bottom of `.env`.

### Production behind nginx

`deploy/nginx.conf.template` is a reverse-proxy config. Render it with your domain:

```bash
SITE_DOMAIN=api.example.org envsubst '${SITE_DOMAIN}' \
  < deploy/nginx.conf.template > /etc/nginx/sites-available/ocs-spring.conf
```

Then enable it and run `certbot --nginx -d api.example.org`. Port 8585 is the API; 8589 is the
group-chat websocket.

## Database

- Default: SQLite at `volumes/sqlite.db` (mounted from the host; back up this folder).
- MySQL: set `DB_URL=jdbc:mysql://host:3306/db`, `DB_USERNAME`, `DB_PASSWORD`, and
  `DDL_AUTO=update` for the first boot against an empty database.
- Schema mode: when `DDL_AUTO` is unset, the app uses `update` if the SQLite file does not
  exist yet and `none` otherwise.

Maintenance scripts live in `scripts/` (Python 3; they read `.env`). The main entry point is `python3 scripts/db_migrate.py` with
`status`, `check --live`, `backup`, `init` and `restore` subcommands.

## Develop without Docker

Requires Java 21.

```bash
cp .env.example .env      # set KIT_DEV_MODE=true to run without a JWT_SECRET
./mvnw spring-boot:run    # http://127.0.0.1:8585
./mvnw test
```

## Layout

- `src/main/java/com/open/spring/system/` startup, config, seeding (`ModelInit`)
- `src/main/java/com/open/spring/security/` JWT auth, CORS, cookies
- `src/main/java/com/open/spring/mvc/` one package per feature (entity, repository, API controller)
- `src/main/resources/templates/` admin UI templates
- `runner/` sandboxed Java code runner used by the `code_runner` compose service

## License

MIT, see `LICENSE`. Based on the Open Coding Society project.
