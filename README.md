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
   the admin, staff and member accounts plus sample data for the enabled features. Later
   starts reuse the same database.

3. Check it:

   ```bash
   curl http://localhost:8585/api/kit
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

## Choosing a kit

Set `KIT` in `.env` to pick a preset. Every feature's code ships in the image; a disabled
feature answers 404 on its URLs, its scheduled jobs and startup seeders do not run, and its
admin nav links are hidden. Its tables are still created, so turning it on later just works.

| Kit | For | Roles (admin / staff / member) |
|---|---|---|
| `vanilla` | A baseline site you can hand to students: accounts, profile, blog and notebooks. | Admin / Editor / User |
| `education` | A full CS classroom. | Admin / Teacher / Student |
| `business` | Organizations without students; everything except classroom features. | Admin / Manager / Member |
| unset | Every feature on (how the platform behaved before kits). Education role names. | Admin / Teacher / Student |

| Feature | Category | Vanilla | Education | Business | Spring packages (under `com.open.spring`) |
|---|---|:-:|:-:|:-:|---|
| `core` (always on) | core | yes | yes | yes | mvc: person, groups, identity, userPreferences, backups, extract, generic, table, S3uploads, dbFix; system, security |
| `lessons` | education |  | yes |  | lessonProgress |
| `code-runner` | general |  | yes |  | javarunner, Make_debugger |
| `assignments` | education |  | yes |  | assignments, grades, gradePrediction, synergy, progressBar, quiz, quests, certificate, teamteach, sprintDates, evaluationAdmin, evaluationStudent, assess, challengeSubmission, onboarding_hacks_rubric |
| `game-engine` | general |  | yes |  | rpg, games, PauseMenu, multiplayer, leaderboard, trains, plant |
| `ai-chatbot` | general |  | yes | yes | geminiChatbotTest, geminiUserPreferences, generator, voice |
| `ai-grading` | education |  | yes |  | geminiFRQgrading, automaticFRQFeedback, chatter (+ `/api/submissions/ai-grade`, `/api/grade-frqs`) |
| `social` | general |  | yes | yes | comment, note, announcement, chat, directmessages, email (+ group chat) |
| `calendar` | general |  | yes | yes | slack |
| `analytics` | general |  | yes | yes | analytics, stats |
| `hallpass` | education |  | yes |  | bathroom, student |
| `finance-sim` | general |  |  |  | bank, crypto, cryptoMining, userStocks, stocksFetch, poker, blackjack, dice, mines, hardAssets |
| `resume` | general |  | yes | yes | resume, resumes |
| `kasm` | general |  |  |  | none (frontend/Flask only) |
| `face-recognition` | general |  |  |  | mortevision (+ `/api/face`) |
| `examples` | general |  |  |  | jokes, greet, factorial, palindrome, Fibonacci, birds, titanic, dataFun, media, profileThymeleaf, hacks, scraper |

The exact URL prefixes per feature are in `src/main/resources/kits/catalog.json`. At startup
the log lists the kit and every feature as `[on ]`/`[off]` with its category, labels and
where the setting came from. `GET /api/kit` (public) returns the same for frontends:

```json
{"kit": "education", "kit_name": "OCS Education", "features": ["core", "lessons", ...],
 "feature_details": [{"id": "core", "name": "Core platform", "category": "core", "labels": [...], "enabled": true}, ...],
 "roles": {"admin": {"label": "Admin"}, "staff": {"label": "Teacher"}, "member": {"label": "Student"}},
 "signup_role": "member"}
```

### Roles

Each kit names the three canonical roles differently and seeds one account per role
(`ADMIN_*`, `TEACHER_*` and `USER_*` in `.env` set the admin, staff and member accounts and
override the kit's default uid/name/email). Internally the roles stay `ROLE_ADMIN`,
`ROLE_TEACHER` (staff) and `ROLE_STUDENT` (member), all with `ROLE_USER`; only the labels
in the admin UI and `/api/kit` change. New sign-ups get the kit's `signup_role`.

## Customizing features

Three layers, later ones win:

1. `KIT` picks the preset.
2. `FEATURES` in `.env` adjusts it at startup:
   - `FEATURES=+finance-sim,-hallpass` adds/removes relative to the kit;
   - `FEATURES=lessons,code-runner` is an explicit list (core is always added);
   - `FEATURES=*` turns everything on, `FEATURES=*,-face-recognition` everything but one.

   Unknown ids, unknown kits and `-core` stop startup with a message listing valid values.
3. Admins override features at runtime, no restart needed (stored in the `feature_override`
   table). Only `ROLE_ADMIN` may do this; teachers/staff and members get 403.

   ```bash
   TOKEN=...   # the jwt_java_spring cookie from POST /authenticate as an admin
   curl -b "jwt_java_spring=$TOKEN" http://localhost:8585/api/kit/admin          # sources, setup guides, readiness
   curl -b "jwt_java_spring=$TOKEN" -X PUT http://localhost:8585/api/kit/admin/features \
        -H 'Content-Type: application/json' -d '{"features": {"finance-sim": true, "hallpass": false}}'
   curl -b "jwt_java_spring=$TOKEN" -X DELETE http://localhost:8585/api/kit/admin/features/hallpass  # back to KIT/FEATURES
   ```

   `/api/kit/admin` adds, per feature, `source` (`kit`, `env` or `admin`), the setup guide
   (summary, the Spring settings with `configured: true/false` - never their values - steps,
   how to verify) and `ready` (all required settings present). Changes are logged with the
   admin's uid and time. Enabling a feature at runtime seeds its sample data if its tables
   are empty and runs any startup task it skipped.

## Adding a new feature

1. Add the feature id (name, category, labels, setup) and its kit membership to the
   canonical `OCS-Kit/kits/catalog.json`, then run `python3 scripts/sync_kit_catalog.py` here.
2. In `src/main/resources/kits/catalog.json`, fill the feature's `spring` block: its
   `packages` (e.g. `mvc.myfeature`) and the URL `paths` its controllers use
   (e.g. `/api/myfeature`, `/mvc/myfeature`).
3. Put the code in that package. Gating needs no extra code: requests, `@Scheduled` jobs,
   event listeners and `CommandLineRunner` seeders are matched to the feature by package and
   path. Seed sample data in `ModelInit.seeds()` with the entity class, or with a runner in
   the package. Mark schema migrations that must always run with `@KitAlwaysRun`. Gate nav
   links with `th:if="${@kitFeatures.pathEnabled('/mvc/myfeature')}"`.
4. Run `./mvnw test`. `KitCatalogValidationTest` fails if a package or endpoint is not mapped,
   if two features claim the same package or prefix, or if the catalog drifted from the
   canonical one.

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
