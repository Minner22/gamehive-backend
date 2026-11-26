# GameHive — backend

[![CI](https://github.com/Minner22/gamehive-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/Minner22/gamehive-backend/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Minner22/gamehive-backend/actions/workflows/codeql.yml/badge.svg)](https://github.com/Minner22/gamehive-backend/actions/workflows/codeql.yml)
[![Release](https://img.shields.io/github/v/release/Minner22/gamehive-backend)](https://github.com/Minner22/gamehive-backend/releases)
[![Image](https://img.shields.io/badge/image-ghcr.io-blue?logo=docker)](https://github.com/Minner22/gamehive-backend/pkgs/container/gamehive-backend)

[English](README.md) | **Polski**

REST API platformy **GameHive** dla graczy planszowych: wspólna biblioteka gier, którą uzupełnia społeczność,
a porządku pilnują moderatorzy, prywatne kolekcje oraz wyszukiwanie pełnotekstowe z podpowiedziami.
Klient webowy jest w repozytorium [gamehive-frontend](https://github.com/Minner22/gamehive-frontend).

Modularny monolit na **Spring Boot 4** i **Javie 25**. Grupy, wypożyczenia, oceny i statystyki są kolejne
w [roadmapie](#roadmapa).

## Funkcje

- **Konta:** rejestracja z aktywacją mailem, logowanie, reset hasła linkiem z maila, własny profil, usunięcie
  własnego konta.
- **Bezpieczne sesje:** krótkie tokeny dostępu, refresh tokeny rotowane przy każdym użyciu — ponowne użycie starego
  wylogowuje użytkownika na wszystkich urządzeniach; dezaktywacja, zmiana hasła i wymuszone wylogowanie przez admina
  natychmiast unieważniają istniejące tokeny.
- **Biblioteka gier:** użytkownicy zgłaszają gry (szkic → oczekujące), moderatorzy zatwierdzają je albo odrzucają
  z podaniem powodu; zatwierdzone gry tworzą wspólną bibliotekę z filtrami (liczba graczy, czas gry, wiek, kategoria,
  mechanika, wydawca…).
- **Dodatki**, które dziedziczą z gry bazowej wszystko, czego same nie nadpisują (liczbę graczy, czas, kategorie…),
  więc API zwraca obok siebie wartości własne i efektywne.
- **Słowniki:** kuratorowane kategorie i mechaniki; wydawcy i autorzy powstają w locie przy zgłaszaniu gry
  i są zatwierdzani razem z nią.
- **Prywatne kolekcje** gier i dodatków oraz lekki endpoint „które id już mam” dla interfejsu.
- **Wyszukiwanie:** pełnotekstowe po bibliotece z tymi samymi filtrami oraz odporne na literówki podpowiedzi
  wydawców i autorów (Meilisearch).
- **Panel admina:** role, dezaktywacja, usuwanie kont, wymuszone wylogowanie, chroniony ostatni admin oraz dziennik
  audytu każdej decyzji dotyczącej kont i moderacji (kto, komu, co, kiedy).

## Stos technologiczny

| Obszar | Technologia |
|---|---|
| Język | Java 25 (wątki wirtualne) |
| Framework | Spring Boot 4.2 (Web MVC, Security, Data JPA, Validation, Cache, Mail, Actuator) |
| Baza danych | PostgreSQL 16, migracje Flyway 12 |
| Cache / tokeny | Redis 7 (refresh tokeny, blacklista, epoki sesji, blokady) + Caffeine |
| Wyszukiwarka | Meilisearch v1.53 (`meilisearch-java` 0.22.1) |
| Bezpieczeństwo | JWT HS256 (Nimbus JOSE), BCrypt |
| Mapowanie | MapStruct + Lombok |
| Dokumentacja API | springdoc-openapi (Swagger UI) |
| Testy | JUnit 5, MockMvc, Testcontainers (PostgreSQL, Meilisearch), H2, wbudowany Redis |
| Build / CI | Maven Wrapper, GitHub Actions, JaCoCo + SonarCloud, CodeQL, Dependabot |
| Obraz | Cloud Native Buildpacks (Paketo) → GitHub Container Registry |

## Wymagania

- JDK 25
- Docker z Docker Compose (PostgreSQL, Redis i Meilisearch przy lokalnym uruchomieniu; Testcontainers w testach
  integracyjnych)
- Maven nie jest potrzebny osobno — używaj wrappera: `./mvnw` (na Windowsie `mvnw.cmd`)

## Uruchomienie

1. Utwórz jeden plik `.env` obok `pom.xml` (jest w `.gitignore`). Czyta go Docker Compose dla kontenerów usług,
   a aplikacja importuje go także dla siebie:

   ```properties
   REDIS_PASSWORD=<hasło redis>
   POSTGRES_PASSWORD=<hasło postgres>
   POSTGRES_USER=gamehive
   MEILI_MASTER_KEY=<klucz meilisearch>
   JWT_ACTIVATION_SECRET=<sekret>
   JWT_REFRESH_SECRET=<sekret>
   JWT_ACCESS_SECRET=<sekret>
   JWT_PASSWORDRESET_SECRET=<sekret>
   MAIL_USERNAME=<e-mail smtp>
   MAIL_PASSWORD=<hasło aplikacji smtp>
   ```

   Wartości wpisuj **bez cudzysłowów** (Compose je zdejmuje, Spring nie). Sekrety JWT najlepiej wygenerować jako
   losowe ciągi hex.

2. Uruchom aplikację — profil `dev` (domyślny) sam startuje i zatrzymuje kontenery z `docker-compose.yml`:

   ```bash
   ./mvnw spring-boot:run
   ```

   API pod `http://localhost:8080`, Swagger UI pod `http://localhost:8080/swagger-ui.html`.

Profil `dev` zakłada dwa konta (hasło `password123`): `john.doe@example.com` (admin) i `jane.smith@example.com`
(użytkownik) oraz 18 zatwierdzonych gier, 12 dodatków i kilka oczekujących zgłoszeń. Indeks wyszukiwarki startuje
pusty — wypełnij go jako admin przez `POST /api/v1/admin/search/reindex`.

## Obraz Docker

Każdy merge do `master` publikuje obraz w GitHub Container Registry: `ghcr.io/minner22/gamehive-backend`
z tagami `master` i `sha-<commit>`; tag wydania `vX.Y.Z` dodaje `X.Y.Z` i `latest`. Obraz działa jako użytkownik
bez uprawnień roota, nie ma powłoki i nie zawiera sekretów — konfigurują go zmienne środowiskowe (profil `prod`).

Uruchomienie razem z usługami z `docker-compose.yml`, z tym samym `.env`:

```bash
docker compose --profile app up -d      # PostgreSQL + Redis + Meilisearch + aplikacja (obraz :master)
curl localhost:8080/actuator/health     # {"status":"UP",...}
docker compose logs -f app              # logi w formacie JSON
```

`GAMEHIVE_IMAGE` wybiera inny tag, `GAMEHIVE_PORT` inny port hosta (domyślnie 8080). Lokalny build obrazu:
`./mvnw spring-boot:build-image -DskipTests`.

Zmienne środowiskowe profilu `prod` (brak którejkolwiek zatrzymuje start z czytelnym błędem):
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` (domyślnie 6379), `REDIS_PASSWORD`, `MEILI_HOST`,
`MEILI_MASTER_KEY`, `JWT_ACTIVATION_SECRET`, `JWT_REFRESH_SECRET`, `JWT_ACCESS_SECRET`, `JWT_PASSWORDRESET_SECRET`,
`MAIL_USERNAME`, `MAIL_PASSWORD` oraz `ACTIVATION_ADDRESS` / `PASSWORD_RESET_ADDRESS` — strony frontu, na które
prowadzą linki z maili (backend dokleja `?token=<JWT>`). Na `prod` Swagger jest wyłączony, a logi idą na stdout
jako JSON. Ustaw kontenerowi limit pamięci — od niego zależy rozmiar sterty JVM.

## Testy

```bash
./mvnw test                 # ~1100 testów na H2 i wbudowanym Redisie, bez Dockera
./mvnw -Pintegration test   # migracje Flyway na prawdziwym PostgreSQL i wyszukiwanie na prawdziwym Meilisearch (Testcontainers)
```

Zwykłe `test` nie dowodzi niczego o migracjach ani o Meilisearch (Flyway wyłączony, wyszukiwarka na fallbacku) —
od tego jest profil `integration`, a CI uruchamia oba przy każdym pull requeście.

## Jak to działa

Kilka decyzji, które kształtują kod:

- **Jedna encja ze statusem zamiast osobnego „zgłoszenia”.** Gra jest szkicem, oczekującą, odrzuconą albo
  zatwierdzoną; biblioteka to po prostu „zatwierdzone”, „moje zgłoszenia” to moje niezatwierdzone. Zatwierdzenie
  niczego nie kopiuje.
- **Indeks wyszukiwarki to podpowiedź, źródłem prawdy jest baza.** Dokumenty niosą tylko pola wyszukiwalne
  i filtrowalne; każde trafienie jest dociągane z bazy, więc nieaktualny dokument nigdy nie pokaże nieaktualnej treści.
  Indeks jest zapisywany po zatwierdzeniu transakcji, w jednym wątku w tle, więc wolna wyszukiwarka nie blokuje
  moderatora.
- **Skutki uboczne dzieją się dopiero po commicie.** Wpisy audytu, aktualizacje indeksu, unieważnianie tokenów i maile
  reagują na zdarzenia `AFTER_COMMIT` — wycofana operacja nie zostawia śladu.
- **Błędy są typowane i statyczne.** Każdy błąd to `ErrorCode` z własnym statusem HTTP i stałym komunikatem; klient
  nigdy nie zobaczy w treści błędu danych z żądania ani szczegółów bibliotek.

Na przykład zatwierdzenie zgłoszenia:

```
POST /api/v1/games                          {"title": "...", "submit": true, ...}   → 201, status PENDING
POST /api/v1/moderation/games/{id}/approve                                         → 200, status APPROVED
     ├─ oczekujący wydawcy i autorzy gry są zatwierdzani razem z nią
     ├─ po commicie: wpis audytu APPROVE (moderator, gra, czas, correlation id)
     └─ po commicie, w tle: dokument gry trafia do indeksu wyszukiwarki
GET  /api/v1/games/search?q=...&players=3                                          → gra, odczytana z bazy
```

## API

Wszystkie endpointy są pod `/api/v1`; wszystko poza `/auth/**` wymaga nagłówka `Authorization: Bearer <access token>`.

| Obszar | Prefiks | Kto |
|---|---|---|
| Uwierzytelnianie | `/auth` | publiczne |
| Własne konto | `/users/me` | zalogowany użytkownik |
| Biblioteka, zgłoszenia, wyszukiwanie | `/games`, `/expansions` | zalogowany użytkownik |
| Słowniki i podpowiedzi | `/taxonomy` | zalogowany użytkownik |
| Kolekcja | `/collection` | zalogowany użytkownik (tylko własne dane) |
| Moderacja | `/moderation/games`, `/moderation/expansions` | moderator, admin |
| Zarządzanie słownikami, reindeks | `/admin/taxonomy`, `/admin/search` | moderator, admin |
| Użytkownicy i dziennik audytu | `/admin/users`, `/admin/audit` | admin |

Sondy zdrowia: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` (publiczne).
Pełna lista endpointów jest w [API.md](API.md), schematy żądań i odpowiedzi w Swagger UI.

## CI

GitHub Actions przy każdym pull requeście i pushu do `master`: testy jednostkowe, testy integracyjne na PostgreSQL
i Meilisearch, blokada edycji zastosowanych migracji, przegląd zależności, SonarCloud i CodeQL. `master` przyjmuje
zmiany wyłącznie przez pull requesty z zielonymi checkami. Push do `master` i tagi wydań dodatkowo budują i publikują
obraz Docker.

## Roadmapa

Zadania są śledzone w [projekcie GameHive na GitHubie](https://github.com/users/Minner22/projects/3),
wydane wersje na stronie [Releases](https://github.com/Minner22/gamehive-backend/releases).

- ✅ **0.1.0:** konta i sesje JWT z rotacją refresh tokenów, panel admina z dziennikiem audytu, biblioteka gier
  z moderacją, dodatki, kolekcje, wyszukiwanie pełnotekstowe z podpowiedziami, CI/CD z obrazem Docker.
- ⬜ Avatary (zdjęcia profilowe).
- ⬜ Grupy: tworzenie, dołączanie, zarządzanie.
- ⬜ Wypożyczanie gier w grupie.
- ⬜ Oceny i recenzje.
- ⬜ Rozgrywki i statystyki.
- ⬜ Wdrożenie (po osiągnięciu MVP).

Jak wydawane są nowe wersje: [RELEASING.md](RELEASING.md).
