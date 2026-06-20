# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build, test, run (Maven; **JDK 25** required):

```bash
mvn clean install              # full build + tests
mvn test                       # all tests
mvn -Dtest=ClassName test                          # one test class
mvn -Dtest=ClassName#methodName test               # one test method
mvn spring-boot:run                                # run app (dev profile, default)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
    -Dsonar.projectKey=Minner22_gamehive-backend   # CI equivalent (SonarCloud)
```

The `dev` profile uses `spring-boot-docker-compose` which **auto-starts/stops `docker-compose.yml`** (PostgreSQL + Redis) on app boot. Requires `secret.properties` (gitignored) and `.env` with `REDIS_PASSWORD`/`POSTGRES_PASSWORD` next to `pom.xml`. The `test` profile needs neither — it uses H2 + embedded Redis (port 16379) and overrides every JWT secret inline in `application-test.yml`.

## Architecture

Spring Boot 4 modular monolith. Java records for DTOs, Lombok everywhere, MapStruct for entity↔DTO mapping (binding via `lombok-mapstruct-binding` annotation processor).

### Module layout (`pl.m22.gamehive`)
- `auth/` — registration, login, JWT issuing/validation, password reset, account activation. `AuthController` is the only `/api/v1/auth/**` entry point.
- `user/` — `UserController` (self-service `/api/v1/users/me*`) and `AdminUserController` (`/api/v1/admin/users/**`, `@PreAuthorize("hasRole('ADMIN')")`). Domain split into `model/`, `repository/`, `service/`, `mapper/`, `dto/`, `util/`.
- `common/` — `persistence/` (entity base hierarchy: generic `AbstractEntity<ID>` with auditing timestamps via `@PrePersist`/`@PreUpdate` and JPA-correct equals/hashCode by id; `LongEntity` = `@Id Long` IDENTITY, `UuidEntity` = `@Id UUID` via `@UuidGenerator(style = VERSION_7)`), `domain/` value objects, email service, logging (`LoggingAspect` wraps every `@RestController` for `Handled request:` lines + `CorrelationIdFilter` for MDC), and the exception infrastructure.
- `config/DevDataInitializer` — seeds two users (`john.doe@example.com` admin, `jane.smith@example.com` user, password `password123`) **only under `dev` profile**. Tests rely on the same IDs/emails via `data.sql`.

### Security & JWT pipeline
- `SecurityConfig` is stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled, only `/h2-console/**`, `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**` are `permitAll`.
- Custom `authenticationEntryPoint` returns **HTTP 401** (not Spring's default 403) with body `{"errorCode":"ACCESS_DENIED","message":"Authentication required"}`. Tests for unauthenticated requests must expect `isUnauthorized()`.
- `JwtAuthFilter` runs before `UsernamePasswordAuthenticationFilter`, **but `shouldNotFilter` skips `/api/v1/auth/{login,register,refresh}`** — those endpoints never touch the JWT filter.
- **`AuthServiceImpl.login` is enumeration-safe**: unknown e-mail and wrong password return the **same** `INVALID_CREDENTIALS` (401) — never `EMAIL_NOT_FOUND` (404). The password is verified **first and always**; for an unknown e-mail a `passwordEncoder.matches(...)` against a `@PostConstruct`-generated dummy bcrypt hash runs anyway, so response time is constant (no timing oracle). `USER_NOT_ACTIVATED` (403) is thrown **only after a correct password** — so account state leaks to the credential owner only, never to an unauthenticated prober.
- Four token types (`JwtTokenType`): `ACCESS` (15 min), `REFRESH` (7 days, cookie-only, scoped to `/api/v1/auth/refresh`), `ACTIVATION` (24 h), `PASSWORD_RESET` (15 min). Each has its own HS256 secret in `application*.yml` under `jwt.*.secret`.
- Refresh tokens are stored in Redis (`RedisRefreshTokenStore`) with a per-user cap (`maxActiveTokensPerUser: 5`). Used activation/password-reset/access tokens are blacklisted in Redis by JTI (`TokenBlacklistService`). Logout blacklists the access token AND calls `jwtService.revokeUsersTokens(email)` to invalidate every refresh.
- **Immediate token invalidation on deactivate / delete / password-change** (GH-63): `UserServiceImpl` (deactivate/delete/`deleteByEmail`) and `AuthServiceImpl.confirmPasswordReset` publish domain events in `user/event/`, handled by `UserSecurityEventListener` with `@TransactionalEventListener(AFTER_COMMIT)` — side effects fire only if the DB change actually commits. The listener revokes refresh tokens (`RedisRefreshTokenStore.revokeAllByUserEmail`), sets a **session epoch** (`RedisSessionEpochStore`, Redis key `token_invalid_after:{email}`, TTL = ACCESS validity), and evicts the auth-state cache. `UserReactivatedEvent` / `UserRolesUpdatedEvent` only evict the cache — they do **not** clear the epoch, so reactivation never resurrects old tokens (re-login required).
- `JwtAuthFilter` loads `{enabled, authorities, invalidAfter}` via `UserAuthStateProvider` (cached, see below) and itself writes the `ApiError` 401 body for two cases: disabled user → **`ACCOUNT_DISABLED`**, access token whose `iat` predates the epoch → **`TOKEN_REVOKED`**. The blacklist branch and every other invalid-token case instead fall through to the entry point's generic `ACCESS_DENIED`. The authenticated principal is the **email String** (downstream only uses `authentication.getName()`), not a `UserDetails`.
- Per-request user state is cached in-memory via Caffeine (`userAuthState`, 60 s TTL, `CacheConfig`) — the TTL is only a safety net; every mutation evicts the entry immediately. Caffeine is per-instance: under horizontal scaling staleness is bounded by the TTL (full consistency would need a Redis-backed cache / pub-sub eviction).

### Audit log
- **Persistent audit of account mutations** (who/whom/what/when): `UserServiceImpl` (`updateUserRoles` → `ROLE_CHANGE`, plus `DEACTIVATE` / `ACTIVATE` / `DELETE` / `FORCE_LOGOUT`) and `AuthServiceImpl.confirmPasswordReset` (`PASSWORD_CHANGE`, self-service so `actor == target`) publish a **dedicated `UserAuditEvent`** — separate from the GH-63 security events, so `UserSecurityEventListener` stays untouched. `activateUser` takes a `requesterEmail` purely to record the actor (no new guard).
- `AuditLogEventListener` (`@TransactionalEventListener(AFTER_COMMIT)`) delegates to `AuditLogService.record`, which is **`@Transactional(REQUIRES_NEW)`** — a new tx is mandatory because AFTER_COMMIT runs after the business tx already committed/closed, and it must be a **separate bean** (self-invocation would bypass the proxy). Consequence: an audit-write failure does **not** roll back the business action (logging never blocks it); a rolled-back action never delivers the event → no entry (so "exactly one entry per committed action").
- `UserAuditLogEntry` (`LongEntity`, table `user_audit_log`, migration `V4`) has **no FK to `application_users`** — entries must survive a user `DELETE`, so `targetId` / `targetEmail` / `actor` are copied values, not relations. `created_at` (from `AbstractEntity`) is the "when". `details` is nullable `text` — only `ROLE_CHANGE` fills it, with before/after role names as small hand-built JSON (roles are a controlled `ROLE_*` vocabulary, no escaping needed). `correlationId` is snapshotted from MDC (`CorrelationIdFilter.CORRELATION_ID`) at publish time to link the entry to request logs. Entity + `AuditAction` enum live in `user/model` (coverage-excluded); service in `user/service`, event + listener in `user/event`.

### Error handling

All thrown errors extend `BaseException(ErrorCode)`. The three subclasses determine **log level only** — HTTP status comes from `ErrorCode.httpStatus` regardless of subclass. Pick the subclass by what kind of failure it is:

- **`DomainException`** — business rule / invariant violated. Logged at INFO.
  Examples: `EMAIL_ALREADY_EXISTS`, `INVALID_PASSWORD`, `CANNOT_REMOVE_LAST_ADMIN`.
- **`ApplicationException`** — use-case flow problem: lookup miss, token validity, precondition not met. Logged at WARN.
  Examples: `USER_NOT_FOUND`, `JWT_EXPIRED`, `JWT_BLACKLISTED`.
- **`InfrastructureException`** — external system or technical layer failed (Redis, SMTP, crypto). Logged at ERROR with stack trace. Always wraps a caught third-party exception.
  Examples: `REDIS_UNAVAILABLE`, `EMAIL_SEND_FAILED`, `JWT_SIGNING_ERROR` (JOSE).

`ErrorCode.java` is grouped into sections (`Domain` / `Application` / `Infrastructure` / `Handler-only`) matching this taxonomy — the section a code lives in tells you which exception class to throw. Adding a new failure mode means: (1) add an `ErrorCode` entry in the matching section with its `HttpStatus`, (2) throw the matching exception subclass. `GlobalExceptionHandler` dispatches by subclass to apply the right log level; the response body (`ApiError(code, message)`) is identical regardless of subclass.

The three "Handler-only" codes (`ACCESS_DENIED`, `VALIDATION_ERROR`, `INTERNAL_ERROR`) are never thrown directly — they're used by `GlobalExceptionHandler` when wrapping framework exceptions (`AuthorizationDeniedException`, `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException` → 400 `VALIDATION_ERROR` for malformed path vars such as a bad UUID, fallback `Exception`).

### API documentation (OpenAPI/Swagger)
- springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) generates the spec. Global metadata + the `bearerAuth` security scheme (HTTP/bearer/JWT) live in `config/OpenApiConfig` (`@OpenAPIDefinition` + `@SecurityScheme`); `config` is coverage-excluded. UI at `/swagger-ui.html`, raw doc at `/v3/api-docs` (both already `permitAll` in `SecurityConfig`). **Disabled under `prod`** via `springdoc.api-docs.enabled=false` / `swagger-ui.enabled=false` in `application-prod.yml`.
- Convention: each controller has a `@Tag`; secured controllers carry class-level `@SecurityRequirement(name = "bearerAuth")` (auth endpoints are public, so no requirement). Endpoints use `@Operation` + `@ApiResponses`; error bodies reference `@Schema(implementation = ApiError.class)` (or `ApiValidationError.class` for 400). `AdminUserController` declares the shared 401/403/500 responses **once at class level** (springdoc merges them with method-level ones) — follow that pattern instead of repeating common codes per method.
- Request/response DTOs are documented with `@Schema` on record components (`dto` is coverage-excluded). Auth success bodies use dedicated records `AccessTokenResponseDto` (login/refresh) and `MessageResponseDto` (register/activate) — added so the spec has real schemas instead of raw `Map`/`String`. Note: `register`/`activate` therefore return `application/json`, not `text/plain`.
- The `OpenApiDocumentationTest` smoke test asserts `/v3/api-docs` returns 200 with the expected title, the `bearerAuth` scheme, and key paths — keep it green when changing OpenAPI config.

### Persistence
- Prod: PostgreSQL with **Flyway** (`src/main/resources/db/migration/V*__*.sql`). `ddl-auto: none` — schema changes go through new `V*` files. **Spring Boot 4 split auto-config into per-tech modules — Flyway runs only with the `spring-boot-starter-flyway` dependency; bare `flyway-core` no longer self-configures (migrations silently won't run).**
- Test: H2 in PostgreSQL-compat mode, `ddl-auto: create-drop`, **Flyway disabled**, `data.sql` populates fixtures (`schema-locations: []` is intentional — Hibernate creates the schema, only data.sql runs).
- `AppUser` ↔ `UserRole` is many-to-many through `user_roles` join table. `UserRole.name` values are stored with the `ROLE_` prefix (e.g. `ROLE_ADMIN`, `ROLE_USER`).
- `AppUser` uses a **UUID v7** primary key (`UuidEntity`, `@UuidGenerator(style = VERSION_7)`; id assigned in-memory at `persist()` — no DB round-trip). Every other entity keeps `Long` IDENTITY (`LongEntity`). FKs to `application_users` (e.g. `user_roles.user_id`) are `uuid`. `V3__migrate_app_user_to_uuid.sql` converts the schema via **drop & recreate** (truncates `application_users`/`user_roles`) — destructive, valid only because there is no production data.

## CI

GitHub Actions (`.github/workflows/build.yml`) runs `mvn -B verify ... sonar:sonar` on every PR. **PRs are built against a merge commit (branch + master)**, so if master moved ahead, local-only `mvn test` may pass while CI fails — pull/rebase master before debugging green-local/red-CI failures. SonarCloud coverage excludes `dto`, `model`, `config`, `mapper`, `exception`, `logging` packages and `GameHiveApplication`.
