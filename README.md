# GameHive — Backend

Backend platformy do zarządzania kolekcjami gier planszowych, grupami graczy oraz
wypożyczeniami. Aktualnie zrealizowany jest moduł **użytkowników i autoryzacji**;
pozostałe moduły (gry, grupy, wypożyczenia, oceny, statystyki) są w planach.

Modularny monolit oparty o **Spring Boot 4** i **Java 25**.

---

## Stos technologiczny

| Obszar            | Technologia |
|-------------------|-------------|
| Język / runtime   | Java 25 |
| Framework         | Spring Boot 4.0.5 (Web, Security, Data JPA, Cache, Validation, Mail, AOP) |
| Baza danych       | PostgreSQL 16 (prod/dev) · H2 w trybie zgodności PostgreSQL (testy) |
| Migracje          | Flyway (`spring-boot-starter-flyway`) |
| Cache / sesje     | Redis (refresh tokeny, blacklista, session epoch) + Caffeine (cache in-memory) |
| JWT               | Nimbus JOSE + JWT (HS256) |
| Mapowanie DTO     | MapStruct (+ Lombok) |
| Dokumentacja API  | springdoc-openapi (Swagger UI) |
| Logowanie         | Logback + JSON encoder, correlation-id (MDC) |
| Build / testy     | Maven, JaCoCo, SonarCloud |

---

## Wymagania

- **JDK 25**
- **Maven**
- **Docker** + Docker Compose (profil `dev` automatycznie startuje PostgreSQL i Redis)

---

## Szybki start (profil `dev`)

Profil `dev` jest domyślny. Wykorzystuje `spring-boot-docker-compose`, który
**automatycznie uruchamia i zatrzymuje** kontenery z `docker-compose.yml`
(PostgreSQL + Redis) wraz ze startem/zatrzymaniem aplikacji.

### 1. Pliki konfiguracyjne (gitignored)

W katalogu obok `pom.xml` utwórz dwa pliki:

**`.env`** — zmienne dla `docker-compose.yml`:

```properties
REDIS_PASSWORD=<haslo_redis>
POSTGRES_PASSWORD=<haslo_postgres>
POSTGRES_USER=gamehive
```

**`secret.properties`** — sekrety aplikacji (importowane przez `application.yml`):

```properties
# Sekrety JWT (HS256) — osobny sekret na każdy typ tokenu
jwt.activation.secret=<sekret>
jwt.refresh.secret=<sekret>
jwt.access.secret=<sekret>
jwt.passwordreset.secret=<sekret>

# Konto SMTP (np. Gmail z hasłem aplikacyjnym) do wysyłki maili
spring.mail.username=<email>
spring.mail.password=<haslo_aplikacyjne>

# Te same wartości co w .env (używane przez aplikację do połączenia)
REDIS_PASSWORD=<haslo_redis>
POSTGRES_PASSWORD=<haslo_postgres>
```

> Sekrety JWT najlepiej wygenerować jako losowe ciągi hex (sekret tokenu
> `access` jest dłuższy — patrz przykład w repozytorium).

### 2. Uruchomienie

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Konta deweloperskie

Pod profilem `dev` `DevDataInitializer` zakłada dwóch aktywnych użytkowników
(hasło: `password123`):

| E-mail                   | Role                   |
|--------------------------|------------------------|
| `john.doe@example.com`   | `ROLE_ADMIN`, `ROLE_USER` |
| `jane.smith@example.com` | `ROLE_USER`            |

---

## Build i testy

```bash
mvn clean install                         # pełny build + testy
mvn test                                  # wszystkie testy
mvn -Dtest=ClassName test                 # jedna klasa testowa
mvn -Dtest=ClassName#methodName test      # jedna metoda testowa
mvn spring-boot:run                        # uruchom (profil dev)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Profil `test` nie wymaga `secret.properties` ani `.env` — używa **H2** oraz
**wbudowanego Redisa** (port 16379), a wszystkie sekrety JWT są nadpisane
w `application-test.yml`.

---

## Profile

| Profil | Baza danych                | Migracje        | Redis             | Swagger | Uwagi |
|--------|----------------------------|-----------------|-------------------|---------|-------|
| `dev`  | PostgreSQL (docker-compose)| Flyway          | Redis (docker)    | tak     | seeduje konta dev, auto-start kontenerów |
| `test` | H2 (in-memory)             | wyłączone, `data.sql` | embedded (16379) | tak | sekrety nadpisane inline |
| `prod` | PostgreSQL (zmienne env)   | Flyway          | Redis (zmienne env) | **nie** | `ddl-auto: validate`, dokumentacja API wyłączona |

W profilu `prod` konfiguracja pochodzi w całości ze zmiennych środowiskowych:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`,
`ACTIVATION_ADDRESS`, `PASSWORD_RESET_ADDRESS` oraz sekrety JWT i poświadczenia SMTP.

`ACTIVATION_ADDRESS` i `PASSWORD_RESET_ADDRESS` to **adresy stron frontu** (np.
`https://gamehive.example/activate`, `https://gamehive.example/password-reset/confirm`),
na które prowadzą linki w mailach — backend dokleja do nich `?token=<JWT>`. Strona frontu
odczytuje parametr `token` i sama woła odpowiedni endpoint API.

---

## Architektura

Pakiet bazowy: `pl.m22.gamehive`. DTO jako rekordy Javy, Lombok, MapStruct.

- **`auth/`** — rejestracja, aktywacja konta, logowanie, odświeżanie i wylogowanie,
  reset hasła oraz wydawanie/walidacja JWT. Jedyny punkt wejścia: `/api/v1/auth/**`.
- **`user/`** — `UserController` (self-service `/api/v1/users/me*`),
  `AdminUserController` (`/api/v1/admin/users/**`, `ROLE_ADMIN`),
  `AdminAuditController` (`/api/v1/admin/audit`, `ROLE_ADMIN`).
- **`common/`** — warstwa współdzielona: hierarchia encji (`AbstractEntity`,
  `LongEntity`, `UuidEntity`), value objects (`Email`, `Username`, …), serwis e-mail,
  logowanie (`LoggingAspect`, `CorrelationIdFilter`) oraz infrastruktura wyjątków.
- **`config/`** — konfiguracja OpenAPI, cache, async oraz `DevDataInitializer`.

### Bezpieczeństwo i JWT

- Stateless (`SessionCreationPolicy.STATELESS`), CSRF wyłączone. `permitAll` tylko dla
  `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/h2-console/**`.
- Niezautoryzowane żądania otrzymują **HTTP 401** z ciałem
  `{"errorCode":"ACCESS_DENIED","message":"Authentication required"}`.
- Cztery typy tokenów (każdy z własnym sekretem HS256): `ACCESS` (15 min),
  `REFRESH` (7 dni, ciasteczko HttpOnly o ścieżce `/api/v1/auth/refresh`),
  `ACTIVATION` (24 h), `PASSWORD_RESET` (15 min).
- Refresh tokeny przechowywane w Redis (limit 5 aktywnych na użytkownika).
  Zużyte tokeny aktywacyjne/resetu/access trafiają na blacklistę (po JTI).
- **Natychmiastowe unieważnienie tokenów** przy dezaktywacji / usunięciu / zmianie
  hasła — przez zdarzenia domenowe (`@TransactionalEventListener(AFTER_COMMIT)`),
  session epoch w Redis oraz eksmisję cache `userAuthState` (Caffeine, 60 s TTL).

### Dziennik audytu

Mutacje kont (`ROLE_CHANGE`, `DEACTIVATE`, `ACTIVATE`, `DELETE`, `FORCE_LOGOUT`,
`PASSWORD_CHANGE`) są trwale audytowane w tabeli `user_audit_log` (kto / kogo / co /
kiedy). Wpisy nie mają FK do `application_users`, więc przeżywają usunięcie konta.
Zapis audytu działa w osobnej transakcji (`REQUIRES_NEW`) i nigdy nie blokuje
operacji biznesowej.

### Obsługa błędów

Wszystkie wyjątki dziedziczą po `BaseException(ErrorCode)`. Trzy podklasy wyznaczają
poziom logowania (status HTTP zależy wyłącznie od `ErrorCode`):
`DomainException` (INFO — naruszenie reguły biznesowej), `ApplicationException`
(WARN — problem przepływu / brak rekordu), `InfrastructureException`
(ERROR — awaria systemu zewnętrznego). Odpowiedź zawsze ma postać
`ApiError(errorCode, message)` (lub `ApiValidationError` dla błędów walidacji 400).

### Persystencja

- `AppUser` używa klucza głównego **UUID v7** (`@UuidGenerator(style = VERSION_7)`);
  pozostałe encje — `Long` IDENTITY.
- Zmiany schematu wyłącznie przez nowe pliki `V*__*.sql` (`ddl-auto: none`/`validate`).
- `V3__migrate_app_user_to_uuid.sql` zmienia PK z `BIGINT` na `uuid` metodą
  **drop & recreate** (czyści `application_users`/`user_roles`) — operacja
  destrukcyjna, dopuszczalna tylko z uwagi na brak danych produkcyjnych.

> **Uwaga (Spring Boot 4):** autokonfiguracja została rozbita na moduły per-technologia.
> Flyway działa tylko z zależnością `spring-boot-starter-flyway` — samo `flyway-core`
> nie aktywuje migracji (wykonają się po cichu „nie-wykonają”).

---

## API

Bazowy prefiks: `/api/v1`. Endpointy poza `/auth/**` wymagają nagłówka
`Authorization: Bearer <access_token>`.

### Authentication (`/api/v1/auth`) — publiczne

| Metoda | Ścieżka                     | Opis |
|--------|-----------------------------|------|
| POST   | `/register`                 | Rejestracja (wysyła link aktywacyjny) |
| GET    | `/activate?token=...`       | Aktywacja konta |
| POST   | `/login`                    | Logowanie (access token w body, refresh w ciasteczku) |
| GET    | `/refresh`                  | Odświeżenie access tokenu (z ciasteczka refresh) |
| POST   | `/logout`                   | Wylogowanie (unieważnia tokeny) |
| POST   | `/password-reset/request`   | Żądanie resetu hasła |
| POST   | `/password-reset/confirm`   | Ustawienie nowego hasła |
| POST   | `/activation/resend`        | Ponowne wysłanie maila aktywacyjnego |

### User (`/api/v1/users`) — zalogowany użytkownik

| Metoda | Ścieżka         | Opis |
|--------|-----------------|------|
| GET    | `/me`           | Dane i profil zalogowanego użytkownika |
| PATCH  | `/me/profile`   | Częściowa aktualizacja własnego profilu |

### Admin – Users (`/api/v1/admin/users`) — `ROLE_ADMIN`

| Metoda | Ścieżka                   | Opis |
|--------|---------------------------|------|
| GET    | `/`                       | Stronicowana lista użytkowników |
| GET    | `/{id}`                   | Użytkownik po UUID |
| GET    | `/by-username/{username}` | Użytkownik po nazwie |
| GET    | `/by-email/{email}`       | Użytkownik po e-mailu |
| PUT    | `/{id}/roles`             | Zmiana ról (audyt: `ROLE_CHANGE`) |
| PATCH  | `/{id}/deactivate`        | Dezaktywacja konta (audyt: `DEACTIVATE`) |
| PATCH  | `/{id}/activate`          | Reaktywacja konta (audyt: `ACTIVATE`) |
| DELETE | `/{id}`                   | Usunięcie konta (audyt: `DELETE`) |
| POST   | `/{id}/force-logout`      | Wymuszenie wylogowania (audyt: `FORCE_LOGOUT`) |

### Admin – Audit (`/api/v1/admin/audit`) — `ROLE_ADMIN`

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET    | `/`     | Przeszukiwanie dziennika audytu (filtry: `targetId`, `actor`, `action`, `from`, `to`; stronicowanie) |

Pełna specyfikacja dostępna w Swagger UI (`/swagger-ui.html`).

---

## Roadmap (MVP)

- [x] Rejestracja użytkowników
- [x] Logowanie i autoryzacja (JWT)
- [x] Zarządzanie użytkownikami (self-service + panel admina + audyt)
- [ ] Dodawanie gier
- [ ] Zarządzanie grami
- [ ] Dodawanie grup
- [ ] Dołączanie do grup
- [ ] Zarządzanie grupami
- [ ] Oceny i recenzje
- [ ] Multiplayer i statystyki

---

## CI

GitHub Actions (`.github/workflows/build.yml`) uruchamia `mvn -B verify sonar:sonar`
na każdym PR. **PR budowany jest na commicie scalającym (branch + master)** — jeśli
master się przesunął, lokalny `mvn test` może przejść mimo czerwonego CI; przed
debugowaniem warto zrobić rebase/pull mastera. Pokrycie SonarCloud wyklucza pakiety
`dto`, `model`, `config`, `mapper`, `exception`, `logging` oraz `GameHiveApplication`.