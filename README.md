# GameHive — backend

[![CI](https://github.com/Minner22/gamehive-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Minner22/gamehive-backend/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Minner22/gamehive-backend/actions/workflows/codeql.yml/badge.svg)](https://github.com/Minner22/gamehive-backend/actions/workflows/codeql.yml)
[![Release](https://img.shields.io/github/v/release/Minner22/gamehive-backend)](https://github.com/Minner22/gamehive-backend/releases)
[![Image](https://img.shields.io/badge/image-ghcr.io-blue?logo=docker)](https://github.com/Minner22/gamehive-backend/pkgs/container/gamehive-backend)

**English** | [Polski](README.pl.md)

The REST API behind **GameHive**, a platform for board game players: a shared game library that the community
fills and moderators keep clean, private collections, and full-text search with autocomplete.
The web client lives in [gamehive-frontend](https://github.com/Minner22/gamehive-frontend).

A modular monolith on **Spring Boot 4** and **Java 25**. Groups, lending, ratings and statistics are next on the
[roadmap](#roadmap).

## Features

- **Accounts:** sign-up with e-mail activation, login, password reset by e-mail link, own profile, deleting
  one's own account.
- **Sessions you can trust:** short access tokens, refresh tokens that rotate on every use — reusing an old one logs
  the user out on every device; deactivation, password change and admin force-logout cut existing tokens at once.
- **Game library:** users submit games (draft → pending), moderators approve or reject them with a reason;
  approved games form the shared library with filters (players, playing time, age, category, mechanic, publisher…).
- **Expansions** that inherit what they do not override from the base game (players, time, categories…), so the
  API returns both the own and the effective values.
- **Dictionaries:** curated categories and mechanics; publishers and authors are created on the fly while submitting
  a game and approved together with it.
- **Private collections** of games and expansions, plus a lightweight "which ids do I own" endpoint for the UI.
- **Search:** full-text search over the library with the same filters, and typo-tolerant autocomplete for publishers
  and authors (Meilisearch).
- **Admin panel:** roles, deactivation, deleting accounts, force logout, a protected last admin, and an audit log of
  every account and moderation decision (who, whom, what, when).

## Stack

| Area | Technology |
|---|---|
| Language | Java 25 (virtual threads) |
| Framework | Spring Boot 4.2 (Web MVC, Security, Data JPA, Validation, Cache, Mail, Actuator) |
| Database | PostgreSQL 16, migrations with Flyway 12 |
| Cache / tokens | Redis 7 (refresh tokens, blacklist, session epochs, locks) + Caffeine |
| Search | Meilisearch v1.53 (`meilisearch-java` 0.22.1) |
| Security | JWT HS256 (Nimbus JOSE), BCrypt |
| Mapping | MapStruct + Lombok |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, MockMvc, Testcontainers (PostgreSQL, Meilisearch), H2, embedded Redis |
| Build / CI | Maven Wrapper, GitHub Actions, JaCoCo + SonarCloud, CodeQL, Dependabot |
| Image | Cloud Native Buildpacks (Paketo) → GitHub Container Registry |

## Requirements

- JDK 25
- Docker with Docker Compose (PostgreSQL, Redis and Meilisearch for local runs; Testcontainers for integration tests)
- Maven is not needed separately — use the wrapper: `./mvnw` (on Windows `mvnw.cmd`)

## Running

1. Create one `.env` file next to `pom.xml` (gitignored). Docker Compose reads it for the service containers and the
   app imports it too:

   ```properties
   REDIS_PASSWORD=<redis password>
   POSTGRES_PASSWORD=<postgres password>
   POSTGRES_USER=gamehive
   MEILI_MASTER_KEY=<meilisearch key>
   JWT_ACTIVATION_SECRET=<secret>
   JWT_REFRESH_SECRET=<secret>
   JWT_ACCESS_SECRET=<secret>
   JWT_PASSWORDRESET_SECRET=<secret>
   MAIL_USERNAME=<smtp e-mail>
   MAIL_PASSWORD=<smtp app password>
   ```

   Write the values **without quotes** (Compose strips them, Spring does not). JWT secrets are best random hex strings.

2. Start the app — the `dev` profile (default) starts and stops the containers from `docker-compose.yml` by itself:

   ```bash
   ./mvnw spring-boot:run
   ```

   API on `http://localhost:8080`, Swagger UI on `http://localhost:8080/swagger-ui.html`.

The `dev` profile seeds two accounts (password `password123`): `john.doe@example.com` (admin) and
`jane.smith@example.com` (user), plus 18 approved games, 12 expansions and a few pending submissions.
The search index starts empty — fill it with `POST /api/v1/admin/search/reindex` as the admin.

## Docker image

Every merge to `master` publishes an image to the GitHub Container Registry:
`ghcr.io/minner22/gamehive-backend` with the tags `master` and `sha-<commit>`; a release tag `vX.Y.Z` adds
`X.Y.Z` and `latest`. The image runs as a non-root user, has no shell and carries no secrets — it is configured
with environment variables (profile `prod`).

Run it together with the services from `docker-compose.yml`, using the same `.env`:

```bash
docker compose --profile app up -d      # PostgreSQL + Redis + Meilisearch + the app (image :master)
curl localhost:8080/actuator/health     # {"status":"UP",...}
docker compose logs -f app              # JSON logs
```

`GAMEHIVE_IMAGE` picks another tag, `GAMEHIVE_PORT` another host port (default 8080). Build the image locally with
`./mvnw spring-boot:build-image -DskipTests`.

Environment variables of the `prod` profile (a missing one stops the start with a clear error):
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` (default 6379), `REDIS_PASSWORD`, `MEILI_HOST`,
`MEILI_MASTER_KEY`, `JWT_ACTIVATION_SECRET`, `JWT_REFRESH_SECRET`, `JWT_ACCESS_SECRET`, `JWT_PASSWORDRESET_SECRET`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, and `ACTIVATION_ADDRESS` / `PASSWORD_RESET_ADDRESS` — the frontend pages the e-mail
links point to (the backend appends `?token=<JWT>`). Swagger is off under `prod`; logs go to stdout as JSON.
Give the container a memory limit — the JVM heap is sized from it.

## Tests

```bash
./mvnw test                 # ~1100 tests on H2 and embedded Redis, no Docker needed
./mvnw -Pintegration test   # Flyway migrations on real PostgreSQL and search on real Meilisearch (Testcontainers)
```

The plain `test` run proves nothing about migrations or Meilisearch (Flyway is off, search falls back) —
that is what the `integration` profile is for, and CI runs both on every pull request.

## How it works

A few decisions that shape the code:

- **One entity with a status instead of a separate "submission".** A game is a draft, pending, rejected or
  approved; the library is simply "approved", "my submissions" are my own non-approved ones. Approving copies nothing.
- **The search index is a hint, the database is the truth.** Documents carry only searchable and filterable fields;
  every hit is loaded back from the database, so a stale document can never show stale content. The index is written
  after the transaction commits, on a single background thread, so a slow search engine never blocks a moderator.
- **Side effects happen only after a commit.** Audit entries, index updates, token revocation and e-mails listen for
  `AFTER_COMMIT` events — a rolled-back action leaves no trace.
- **Errors are typed and static.** Every error is an `ErrorCode` with its HTTP status and a fixed message; the client
  never sees request data or library internals in an error body.

For example, approving a submission:

```
POST /api/v1/games                          {"title": "...", "submit": true, ...}   → 201, status PENDING
POST /api/v1/moderation/games/{id}/approve                                         → 200, status APPROVED
     ├─ pending publishers and authors of the game are approved with it
     ├─ after commit: audit entry APPROVE (moderator, game, time, correlation id)
     └─ after commit, in the background: the game document goes to the search index
GET  /api/v1/games/search?q=...&players=3                                          → the game, read from the database
```

## API

All endpoints are under `/api/v1`; everything except `/auth/**` needs `Authorization: Bearer <access token>`.

| Area | Prefix | Who |
|---|---|---|
| Authentication | `/auth` | public |
| Own account | `/users/me` | signed-in user |
| Library, submissions, search | `/games`, `/expansions` | signed-in user |
| Dictionaries and autocomplete | `/taxonomy` | signed-in user |
| Collection | `/collection` | signed-in user (own data only) |
| Moderation | `/moderation/games`, `/moderation/expansions` | moderator, admin |
| Dictionary admin, reindex | `/admin/taxonomy`, `/admin/search` | moderator, admin |
| Users and audit log | `/admin/users`, `/admin/audit` | admin |

Health checks: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` (public).
The full endpoint list (in Polish) is in [API.md](API.md); request and response schemas are in Swagger UI.

## CI

GitHub Actions on every pull request and push to `master`: unit tests, integration tests on PostgreSQL and
Meilisearch, a guard against editing applied migrations, dependency review, SonarCloud and CodeQL. `master` accepts
changes only through pull requests with green checks. Pushes to `master` and release tags also build and publish
the Docker image.

## Roadmap

Tasks are tracked in the [GameHive project on GitHub](https://github.com/users/Minner22/projects/3),
released versions are on the [Releases](https://github.com/Minner22/gamehive-backend/releases) page.

- ✅ **0.1.0:** accounts and JWT sessions with refresh-token rotation, admin panel with audit log, game library with
  moderation, expansions, collections, full-text search and autocomplete, CI/CD with a Docker image.
- ⬜ Avatars (profile pictures).
- ⬜ Groups: creating, joining, managing.
- ⬜ Lending games within a group.
- ⬜ Ratings and reviews.
- ⬜ Plays and statistics.
- ⬜ Deployment (once the MVP is ready).

How versions are released: [RELEASING.md](RELEASING.md) (in Polish).
