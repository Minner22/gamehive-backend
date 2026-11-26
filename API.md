# API — lista endpointów

[← README](README.pl.md)

Pełna lista endpointów backendu GameHive. Dokumentem źródłowym kontraktu jest specyfikacja OpenAPI generowana z kodu (Swagger UI na profilu `dev`) — ta lista to skrót do czytania bez uruchamiania aplikacji.

Bazowy prefiks: `/api/v1`. Endpointy poza `/auth/**` wymagają nagłówka
`Authorization: Bearer <access_token>`.

## Authentication (`/api/v1/auth`) — publiczne

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

## User (`/api/v1/users`) — zalogowany użytkownik

| Metoda | Ścieżka         | Opis |
|--------|-----------------|------|
| GET    | `/me`           | Dane i profil zalogowanego użytkownika |
| PATCH  | `/me/profile`   | Częściowa aktualizacja własnego profilu |
| DELETE | `/me`           | Trwałe usunięcie własnego konta (potwierdzenie hasłem) |

## Games (`/api/v1/games`) — zalogowany użytkownik

| Metoda | Ścieżka        | Opis |
|--------|----------------|------|
| GET    | `/`            | Biblioteka — tylko `APPROVED`, stronicowana; filtry: `publisherId`, `categoryId`, `mechanicId`, `players`, `maxPlayingTime`, `yearPublished`, `age` |
| POST   | `/`            | Nowe zgłoszenie (`submit: true` → `PENDING`, `false` → `DRAFT`); wydawcy i autorzy mogą powstać w locie |
| GET    | `/my`          | Własne zgłoszenia (`DRAFT`/`PENDING`/`REJECTED`), stronicowane |
| GET    | `/{id}`        | Gra zatwierdzona **albo** własna w dowolnym statusie |
| PUT    | `/{id}`        | Edycja własnego zgłoszenia (`DRAFT`/`REJECTED`) |
| POST   | `/{id}/submit` | Wysłanie do moderacji (`DRAFT`/`REJECTED` → `PENDING`) |
| GET    | `/search`      | Wyszukiwanie pełnotekstowe (`q` + filtry biblioteki oraz `targetType`, `authorId`, `baseGameId`); wyniki w kolejności rankingu, strona do 50 pozycji |

## Expansions (`/api/v1/expansions`) — zalogowany użytkownik

| Metoda | Ścieżka        | Opis |
|--------|----------------|------|
| GET    | `/`            | Biblioteka dodatków (`APPROVED`); filtry: `baseGameId`, `categoryId`, `mechanicId` |
| POST   | `/`            | Nowe zgłoszenie dodatku (gra bazowa musi być `APPROVED`) |
| GET    | `/my`          | Własne zgłoszenia dodatków |
| GET    | `/{id}`        | Dodatek zatwierdzony **albo** własny w dowolnym statusie |
| PUT    | `/{id}`        | Edycja własnego zgłoszenia (gry bazowej nie da się zmienić) |
| POST   | `/{id}/submit` | Wysłanie do moderacji |

## Taxonomy (`/api/v1/taxonomy`) — zalogowany użytkownik

| Metoda | Ścieżka                | Opis |
|--------|------------------------|------|
| GET    | `/categories`          | Lista kategorii (słownik kuratorowany, pełna lista) |
| GET    | `/mechanics`           | Lista mechanik (słownik kuratorowany, pełna lista) |
| GET    | `/publishers`          | **Przestarzałe** — lista wydawców ucięta do 200 wpisów; użyj `/publishers/suggest` |
| GET    | `/authors`             | **Przestarzałe** — lista autorów ucięta do 200 wpisów; użyj `/authors/suggest` |
| GET    | `/publishers/suggest`  | Podpowiedzi wydawców (`q`, `limit` 1–50, domyślnie 10) |
| GET    | `/authors/suggest`     | Podpowiedzi autorów (dopasowanie po imieniu, nazwisku i pełnej frazie) |

## Collection (`/api/v1/collection`) — zalogowany użytkownik

| Metoda | Ścieżka                      | Opis |
|--------|------------------------------|------|
| GET    | `/games`                     | Moje gry (stronicowane, z pełnym DTO gry) |
| POST   | `/games/{gameId}`            | Dodanie gry do kolekcji (tylko `APPROVED`) |
| DELETE | `/games/{gameId}`            | Usunięcie gry z kolekcji |
| GET    | `/games/ids`                 | Same identyfikatory moich gier — tablica rosnąco, bez stronicowania (stan „mam / nie mam” we froncie) |
| GET    | `/expansions`                | Moje dodatki (stronicowane) |
| GET    | `/expansions/ids`            | Same identyfikatory moich dodatków |
| POST   | `/expansions/{expansionId}`  | Dodanie dodatku (niezależnie od gry bazowej) |
| DELETE | `/expansions/{expansionId}`  | Usunięcie dodatku z kolekcji |

## Moderation – Games (`/api/v1/moderation/games`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

| Metoda | Ścieżka         | Opis |
|--------|-----------------|------|
| GET    | `/`             | Kolejka zgłoszeń `PENDING` (stronicowana) |
| POST   | `/{id}/approve` | Zatwierdzenie + kaskadowe zatwierdzenie oczekujących wydawców i autorów |
| POST   | `/{id}/reject`  | Odrzucenie z powodem (`{reason}`, wymagany) |
| POST   | `/{id}/unlock`  | Odblokowanie po wyczerpaniu limitu poprawek (`REJECTED` → `DRAFT`) |
| PUT    | `/{id}`         | Edycja gry zatwierdzonej (biblioteka) |
| DELETE | `/{id}`         | Twarde usunięcie (każdy status poza `DRAFT`; blokada, gdy gra ma dodatki) |

## Moderation – Expansions (`/api/v1/moderation/expansions`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

Zestaw lustrzany do gier: `GET /`, `POST /{id}/approve`, `POST /{id}/reject`,
`POST /{id}/unlock`, `PUT /{id}`, `DELETE /{id}`.

## Admin – Taxonomy (`/api/v1/admin/taxonomy`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

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

## Admin – Search (`/api/v1/admin/search`) — `ROLE_MODERATOR` / `ROLE_ADMIN`

| Metoda | Ścieżka     | Opis |
|--------|-------------|------|
| POST   | `/reindex`  | Przebudowa obu indeksów z bazy; 409 `REINDEX_ALREADY_RUNNING`, gdy trwa inna przebudowa |

## Admin – Users (`/api/v1/admin/users`) — `ROLE_ADMIN`

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

## Admin – Audit (`/api/v1/admin/audit`) — `ROLE_ADMIN`

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET    | `/`     | Przeszukiwanie dziennika audytu (filtry: `targetId`, `actor`, `action`, `from`, `to`; stronicowanie) |

## Actuator — publiczne

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| GET | `/actuator/health` | Status aplikacji (`UP`/`DOWN`, bez szczegółów) |
| GET | `/actuator/health/liveness`, `/actuator/health/readiness` | Sondy dla healthchecka kontenera |

Pozostałe endpointy actuatora nie są wystawione.

Pełna specyfikacja (schematy żądań i odpowiedzi, kody błędów) jest w Swagger UI: `/swagger-ui.html` na profilu `dev` (na `prod` dokumentacja API jest wyłączona).
