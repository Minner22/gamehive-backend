# GameHive — Backend

Backend platformy do zarządzania kolekcjami gier planszowych, grupami graczy oraz
wypożyczeniami. Zrealizowane są moduły **użytkowników i autoryzacji**, **biblioteki gier
z moderacją treści** (gry, dodatki, słowniki taksonomii), **prywatnych kolekcji** oraz
**wyszukiwania pełnotekstowego**. W planach pozostają grupy, wypożyczenia, oceny i statystyki.

Modularny monolit oparty o **Spring Boot 4** i **Java 25**.

---

## Stos technologiczny

| Obszar            | Technologia |
|-------------------|-------------|
| Język / runtime   | Java 25 (wątki wirtualne włączone: `spring.threads.virtual.enabled`) |
| Framework         | Spring Boot 4.0.5 (Web, Security, Data JPA, Cache, Validation, Mail, AOP) |
| Baza danych       | PostgreSQL 16 (prod/dev) · H2 w trybie zgodności PostgreSQL (testy) |
| Migracje          | Flyway (`spring-boot-starter-flyway`), obecnie `V1`–`V12` |
| Cache / sesje     | Redis (refresh tokeny, blacklista, session epoch, blokada reindeksu) + Caffeine (cache in-memory) |
| Wyszukiwarka      | Meilisearch v1.53 (`meilisearch-java` 0.21.0) — dwa indeksy: treści i podpowiedzi słowników |
| JWT               | Nimbus JOSE + JWT (HS256) |
| Mapowanie DTO     | MapStruct (+ Lombok) |
| Dokumentacja API  | springdoc-openapi (Swagger UI) |
| Logowanie         | Logback + JSON encoder, correlation-id (MDC) |
| Build / testy     | Maven, JaCoCo, SonarCloud |

---

## Wymagania

- **JDK 25**
- **Maven**
- **Docker** + Docker Compose (profil `dev` automatycznie startuje PostgreSQL, Redis i Meilisearch)

---

## Szybki start (profil `dev`)

Profil `dev` jest domyślny. Wykorzystuje `spring-boot-docker-compose`, który
**automatycznie uruchamia i zatrzymuje** kontenery z `docker-compose.yml`
(PostgreSQL `:5432` + Redis `:6379` + Meilisearch `:7700`) wraz ze startem/zatrzymaniem
aplikacji.

### 1. Pliki konfiguracyjne (gitignored)

W katalogu obok `pom.xml` utwórz dwa pliki:

**`.env`** — zmienne dla `docker-compose.yml`:

```properties
REDIS_PASSWORD=<haslo_redis>
POSTGRES_PASSWORD=<haslo_postgres>
POSTGRES_USER=gamehive
MEILI_MASTER_KEY=<klucz_meili>
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
gamehive.search.api-key=<klucz_meili>
```

> **`MEILI_MASTER_KEY` (`.env`) i `gamehive.search.api-key` (`secret.properties`) muszą mieć
> tę samą wartość** — pierwsza konfiguruje kontener, druga klienta. Zmienna jest wymagana:
> `docker-compose.yml` używa formy `${MEILI_MASTER_KEY:?...}`, więc przy pustej wartości
> kontener **nie wystartuje** — zamiast po cichu wystawić nieuwierzytelnioną wyszukiwarkę
> (pod `MEILI_ENV=development` Meili klucza nie wymaga).

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

`ROLE_MODERATOR` istnieje w seedzie ról (`V2`), ale nie jest przypisana żadnemu kontu dev —
nadaje ją admin przez `PUT /api/v1/admin/users/{id}/roles`.

---

## Build i testy

```bash
mvn clean install                         # pełny build + testy
mvn test                                  # wszystkie testy
mvn -Dtest=ClassName test                 # jedna klasa testowa
mvn -Dtest=ClassName#methodName test      # jedna metoda testowa
mvn spring-boot:run                       # uruchom (profil dev)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Profil `test` nie wymaga `secret.properties` ani `.env` — używa **H2** oraz
**wbudowanego Redisa** (port 16379), a wszystkie sekrety JWT są nadpisane
w `application-test.yml`. Meilisearch nie ma wersji embedded, więc testy biegają
na `gamehive.search.enabled=false` (fallback bez indeksu).

> **Zielony `mvn test` nie weryfikuje migracji ani Meilisearch.** Na profilu `test` Flyway
> jest wyłączony (schemat generuje Hibernate), a implementacje Meili nie są w ogóle tworzone.
> Sprawdzenie jednego i drugiego wymaga jednokrotnego uruchomienia profilu `dev`.

---

## Profile

| Profil | Baza danych                 | Migracje              | Redis               | Meilisearch           | Swagger | Uwagi |
|--------|-----------------------------|-----------------------|---------------------|-----------------------|---------|-------|
| `dev`  | PostgreSQL (docker-compose) | Flyway                | Redis (docker)      | kontener `:7700`      | tak     | seeduje konta dev, auto-start kontenerów |
| `test` | H2 (in-memory)              | wyłączone, `data.sql` | embedded (16379)    | wyłączony (fallback)  | tak     | sekrety nadpisane inline, limit poprawek = 2 |
| `prod` | PostgreSQL (zmienne env)    | Flyway                | Redis (zmienne env) | `MEILI_HOST` + klucz  | **nie** | `ddl-auto: validate`, dokumentacja API wyłączona |

W profilu `prod` konfiguracja pochodzi w całości ze zmiennych środowiskowych:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`,
`MEILI_HOST`, `MEILI_MASTER_KEY`, `ACTIVATION_ADDRESS`, `PASSWORD_RESET_ADDRESS`
oraz sekrety JWT i poświadczenia SMTP.

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
- **`game/`** — biblioteka gier: słowniki taksonomii (`Publisher`, `Category`, `Mechanic`,
  `Author`), encje `Game` i `GameExpansion` z workflow moderacji, audyt moderacji treści
  oraz podpakiet **`game/search/`** (Meilisearch).
- **`collection/`** — prywatna kolekcja użytkownika. Świadomie **osobny moduł**, a nie
  podpakiet `game/`: zależność jest jednokierunkowa (`collection` → `game`/`user`) i nic
  w `game/` nie wie o istnieniu kolekcji.
- **`common/`** — warstwa współdzielona: hierarchia encji (`AbstractEntity`, `LongEntity`,
  `UuidEntity`, `ModeratedLongEntity`), value objects (`Email`, `Username`, …), serwis e-mail,
  wspólne `Specifications`, logowanie (`LoggingAspect`, `CorrelationIdFilter`) oraz
  infrastruktura wyjątków.
- **`config/`** — konfiguracja OpenAPI, cache, executorów async oraz `DevDataInitializer`.

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
- **Logowanie odporne na enumerację**: nieznany e-mail i błędne hasło zwracają ten sam
  `INVALID_CREDENTIALS` (401), a hasło weryfikowane jest zawsze (stały czas odpowiedzi).
  Ta sama zasada obowiązuje w bibliotece: cudze zgłoszenie w statusie innym niż `APPROVED`
  zwraca `GAME_NOT_FOUND` / `EXPANSION_NOT_FOUND` (404), a nie 403.

### Biblioteka gier i moderacja treści

- **Jedna encja ze statusem, bez osobnej „submission”**: globalna biblioteka to
  `moderationStatus = APPROVED`, „moje zgłoszenia” to własne wpisy w `DRAFT`/`PENDING`/`REJECTED`.
  Przy zatwierdzeniu nic się nie kopiuje.
- Workflow (wspólny dla gry i dodatku, w `ModeratedLongEntity`):
  `DRAFT → PENDING → APPROVED` albo `REJECTED → (poprawka) → PENDING`. Liczba poprawek jest
  limitowana (`gamehive.moderation.max-resubmissions`, domyślnie 5); po wyczerpaniu limitu
  moderator może zgłoszenie odblokować (`POST .../unlock`).
- **Słowniki**: kategorie i mechaniki są kuratorowane (tworzy je admin/moderator), natomiast
  wydawcy i autorzy powstają **w locie** przy zgłaszaniu gry — jako `PENDING`. Zatwierdzenie
  gry zatwierdza kaskadowo wszystkich jej oczekujących wydawców i autorów. Usunięcie pozycji
  słownikowej używanej przez grę lub dodatek kończy się `*_IN_USE` (409).
- **Dodatki** (`GameExpansion`) dziedziczą wartości z gry bazowej: puste `minPlayers`,
  `maxPlayers`, `playingTimeMinutes`, `minAge` oraz puste kolekcje kategorii/mechanik oznaczają
  „dziedzicz”. DTO zwraca wartości własne **i** efektywne obok siebie. Gra bazowa musi być
  `APPROVED`, a usunięcie gry mającej dodatki jest blokowane (`GAME_HAS_EXPANSIONS`, 409).
- **Audyt moderacji** (`content_moderation_audit_log`): `SUBMIT`, `RESUBMIT`, `EDIT`, `APPROVE`,
  `REJECT`, `UNLOCK`, `DELETE` — dla gier i dodatków, tym samym wzorcem co audyt kont
  (event → `AFTER_COMMIT` → osobna transakcja `REQUIRES_NEW`).

### Kolekcje

Dwie osobne tabele (`game_collection_items`, `expansion_collection_items`), unikat
`(user_id, target_id)`, `ownershipStatus = OWNED` w MVP. Do kolekcji trafiają **wyłącznie
pozycje `APPROVED`**; dodatek dodaje się niezależnie od gry bazowej. Żaden endpoint nie
przyjmuje `userId` — tożsamość pochodzi z tokenu, więc cudzy wpis jest nieodróżnialny od
nieistniejącego. Twarde usunięcie gry/dodatku kasuje powiązane wpisy kaskadą FK, a po
usunięciu konta sprząta nasłuchiwacz `AFTER_COMMIT` (kolumna `user_id` nie ma FK do
`application_users`).

### Wyszukiwanie pełnotekstowe

- Dwa indeksy Meilisearch: **`gamehive_content`** (gry + dodatki, `GET /api/v1/games/search`)
  oraz **`gamehive_taxonomy`** (podpowiedzi wydawców i autorów, `/api/v1/taxonomy/*/suggest`).
  Kluczowa różnica reguł: do indeksu treści trafiają **tylko wpisy `APPROVED`**, do indeksu
  słowników — wpisy we **wszystkich statusach** (tworzenie w locie reużywa istniejącą nazwę
  niezależnie od statusu, więc ukrycie oczekującego wydawcy prowokowałoby duplikat).
- **Dokumenty niosą tylko pola wyszukiwalne i filtrowalne — odpowiedzi są dociągane z bazy**,
  więc indeks nigdy nie zwróci nieaktualnej treści, a trafienie bez odpowiadającego (albo już
  niezatwierdzonego) rekordu jest po cichu pomijane.
- Indeksowanie jest **sterowane zdarzeniami i asynchroniczne**: dokument powstaje wewnątrz
  transakcji biznesowej, a zapis do Meili wykonuje po jej zatwierdzeniu jednowątkowy executor
  (kolejność FIFO). Awaria wyszukiwarki nie cofa operacji biznesowej ani nie blokuje
  moderatora — zostawia rozjazd indeksu, który naprawia reindeks.
- `POST /api/v1/admin/search/reindex` przebudowuje **oba** indeksy pod jedną blokadą w Redis
  (`search_reindex_lock`); równoległe wywołanie dostaje `REINDEX_ALREADY_RUNNING` (409).
- Filtry po **wydawcy, autorze i roku wydania dopasowują wyłącznie gry** — dodatek nie ma tych
  pól w modelu. Fallback (`gamehive.search.enabled=false`) wyłącza wyszukiwanie treści, ale
  podpowiedzi słowników działają dalej — na zapytaniu SQL zamiast indeksu (bez tolerancji
  literówek i bez rankingu trafności).

### Dziennik audytu kont

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
- Kolumny wskazujące na użytkownika w audytach, zgłoszeniach i kolekcjach (`submittedBy`,
  `reviewedBy`, `actor`, `user_id`) to **zwykłe UUID bez FK** — wpisy mają przeżyć usunięcie
  konta.
- Zmiany schematu wyłącznie przez nowe pliki `V*__*.sql` (`ddl-auto: none`/`validate`).
  Aktualny zakres: `V1`–`V12` (schemat, role, migracja PK na UUID, audyt kont, rozbicie adresu,
  słowniki, gry, statusy autorów, audyt moderacji, dodatki, kolekcje).
- **Nigdy nie edytuj już zastosowanej migracji** — Flyway trzyma jej sumę kontrolną i odmówi
  startu (`FlywayValidateException`). Poprawki wyłącznie nowym plikiem `V*`.
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
| DELETE | `/me`           | Trwałe usunięcie własnego konta (potwierdzenie hasłem) |

### Games (`/api/v1/games`) — zalogowany użytkownik

| Metoda | Ścieżka        | Opis |
|--------|----------------|------|
| GET    | `/`            | Biblioteka — tylko `APPROVED`, stronicowana; filtry: `publisherId`, `categoryId`, `mechanicId`, `players`, `maxPlayingTime`, `yearPublished`, `age` |
| POST   | `/`            | Nowe zgłoszenie (`submit: true` → `PENDING`, `false` → `DRAFT`); wydawcy i autorzy mogą powstać w locie |
| GET    | `/my`          | Własne zgłoszenia (`DRAFT`/`PENDING`/`REJECTED`), stronicowane |
| GET    | `/{id}`        | Gra zatwierdzona **albo** własna w dowolnym statusie |
| PUT    | `/{id}`        | Edycja własnego zgłoszenia (`DRAFT`/`REJECTED`) |
| POST   | `/{id}/submit` | Wysłanie do moderacji (`DRAFT`/`REJECTED` → `PENDING`) |
| GET    | `/search`      | Wyszukiwanie pełnotekstowe (`q` + filtry biblioteki oraz `targetType`, `authorId`, `baseGameId`); wyniki w kolejności rankingu, strona do 50 pozycji |

### Expansions (`/api/v1/expansions`) — zalogowany użytkownik

| Metoda | Ścieżka        | Opis |
|--------|----------------|------|
| GET    | `/`            | Biblioteka dodatków (`APPROVED`); filtry: `baseGameId`, `categoryId`, `mechanicId` |
| POST   | `/`            | Nowe zgłoszenie dodatku (gra bazowa musi być `APPROVED`) |
| GET    | `/my`          | Własne zgłoszenia dodatków |
| GET    | `/{id}`        | Dodatek zatwierdzony **albo** własny w dowolnym statusie |
| PUT    | `/{id}`        | Edycja własnego zgłoszenia (gry bazowej nie da się zmienić) |
| POST   | `/{id}/submit` | Wysłanie do moderacji |

### Taxonomy (`/api/v1/taxonomy`) — zalogowany użytkownik

| Metoda | Ścieżka                | Opis |
|--------|------------------------|------|
| GET    | `/categories`          | Lista kategorii (słownik kuratorowany, pełna lista) |
| GET    | `/mechanics`           | Lista mechanik (słownik kuratorowany, pełna lista) |
| GET    | `/publishers`          | **Przestarzałe** — lista wydawców ucięta do 200 wpisów; użyj `/publishers/suggest` |
| GET    | `/authors`             | **Przestarzałe** — lista autorów ucięta do 200 wpisów; użyj `/authors/suggest` |
| GET    | `/publishers/suggest`  | Podpowiedzi wydawców (`q`, `limit` 1–50, domyślnie 10) |
| GET    | `/authors/suggest`     | Podpowiedzi autorów (dopasowanie po imieniu, nazwisku i pełnej frazie) |

### Collection (`/api/v1/collection`) — zalogowany użytkownik

| Metoda | Ścieżka                      | Opis |
|--------|------------------------------|------|
| GET    | `/games`                     | Moje gry (stronicowane, z pełnym DTO gry) |
| POST   | `/games/{gameId}`            | Dodanie gry do kolekcji (tylko `APPROVED`) |
| DELETE | `/games/{gameId}`            | Usunięcie gry z kolekcji |
| GET    | `/expansions`                | Moje dodatki (stronicowane) |
| POST   | `/expansions/{expansionId}`  | Dodanie dodatku (niezależnie od gry bazowej) |
| DELETE | `/expansions/{expansionId}`  | Usunięcie dodatku z kolekcji |

### Moderation – Games (`/api/v1/moderation/games`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

| Metoda | Ścieżka         | Opis |
|--------|-----------------|------|
| GET    | `/`             | Kolejka zgłoszeń `PENDING` (stronicowana) |
| POST   | `/{id}/approve` | Zatwierdzenie + kaskadowe zatwierdzenie oczekujących wydawców i autorów |
| POST   | `/{id}/reject`  | Odrzucenie z powodem (`{reason}`, wymagany) |
| POST   | `/{id}/unlock`  | Odblokowanie po wyczerpaniu limitu poprawek (`REJECTED` → `DRAFT`) |
| PUT    | `/{id}`         | Edycja gry zatwierdzonej (biblioteka) |
| DELETE | `/{id}`         | Twarde usunięcie (każdy status poza `DRAFT`; blokada, gdy gra ma dodatki) |

### Moderation – Expansions (`/api/v1/moderation/expansions`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

Zestaw lustrzany do gier: `GET /`, `POST /{id}/approve`, `POST /{id}/reject`,
`POST /{id}/unlock`, `PUT /{id}`, `DELETE /{id}`.

### Admin – Taxonomy (`/api/v1/admin/taxonomy`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

| Metoda | Ścieżka                    | Opis |
|--------|----------------------------|------|
| GET / POST / PUT / DELETE | `/categories`, `/categories/{id}` | Pełny CRUD kategorii |
| GET / POST / PUT / DELETE | `/mechanics`, `/mechanics/{id}`   | Pełny CRUD mechanik |
| GET    | `/publishers`              | Stronicowana lista wydawców (filtry `status`, `q`) |
| POST   | `/publishers`              | Utworzenie wydawcy od razu jako `APPROVED` |
| POST   | `/publishers/{id}/approve` | Zatwierdzenie wydawcy (idempotentne) |
| DELETE | `/publishers/{id}`         | Usunięcie (409 `PUBLISHER_IN_USE`, gdy używany) |
| GET    | `/authors`                 | Stronicowana lista autorów (filtry `status`, `q`) |
| POST   | `/authors`                 | Utworzenie autora od razu jako `APPROVED` |
| POST   | `/authors/{id}/approve`    | Zatwierdzenie autora (idempotentne) |
| PUT    | `/authors/{id}`            | Edycja imienia i nazwiska |
| DELETE | `/authors/{id}`            | Usunięcie (409 `AUTHOR_IN_USE`, gdy używany) |

### Admin – Search (`/api/v1/admin/search`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

| Metoda | Ścieżka     | Opis |
|--------|-------------|------|
| POST   | `/reindex`  | Przebudowa obu indeksów z bazy; 409 `REINDEX_ALREADY_RUNNING`, gdy trwa inna przebudowa |

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
- [x] Dodawanie gier (zgłoszenia użytkowników + moderacja)
- [x] Zarządzanie grami (biblioteka, edycja i usuwanie przez moderatora, dodatki)
- [x] Prywatne kolekcje
- [x] Wyszukiwanie pełnotekstowe (Meilisearch + autocomplete słowników)
- [ ] Dodawanie grup
- [ ] Dołączanie do grup
- [ ] Zarządzanie grupami
- [ ] Oceny i recenzje
- [ ] Multiplayer i statystyki

Szczegółowa roadmapa fazy „Gry”: [`docs/roadmap-games.md`](docs/roadmap-games.md).

---

## CI

GitHub Actions (`.github/workflows/build.yml`) uruchamia `mvn -B verify sonar:sonar`
na każdym PR. **PR budowany jest na commicie scalającym (branch + master)** — jeśli
master się przesunął, lokalny `mvn test` może przejść mimo czerwonego CI; przed
debugowaniem warto zrobić rebase/pull mastera. Pokrycie SonarCloud wyklucza pakiety
`dto`, `model`, `config`, `mapper`, `exception`, `logging` oraz `GameHiveApplication`.
