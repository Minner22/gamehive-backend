---
name: verify-dev
description: Uruchamia GameHive na profilu dev (PostgreSQL + Redis + Meilisearch z docker-compose) i weryfikuje zmianę na prawdziwej infrastrukturze — migracje Flyway, integrację z Meilisearch, ścieżkę @Async, ERROR dispatch do /error, zachowanie HTTP przez curl z tokenami seedowanych użytkowników. Użyj, gdy użytkownik prosi o "sprawdź na dev", "odpal aplikację", "zweryfikuj migrację", "czy Meili działa", oraz proaktywnie zaproponuj po każdej zmianie, której `mvn test` nie pokrywa: nowy plik V*__*.sql, cokolwiek w game/search, AsyncConfig, zmiana wersji SDK/zależności w pom.xml, handler w GlobalExceptionHandler zależny od kontenera servletów.
---

# Weryfikacja na profilu `dev`

## Dlaczego ten skill istnieje
Profil `test` używa H2 bez Flyway, `gamehive.search.enabled=false` (fallback zamiast Meili), `SyncTaskExecutor` zamiast puli `@Async` i MockMvc bez kontenera. **Zielony `mvn test` nie dowodzi więc niczego** o: migracjach `V*` (checksumy, DDL, `ON DELETE`, indeksy, DEFAULT-y), Meilisearch (beany `Meili*` nie powstają), wątku `search-index-*` i propagacji MDC, ERROR dispatch do `/error`. To już dwa razy kosztowało: zmodyfikowany zastosowany `V8` (dev zablokowany na 5 tygodni) i brakujący `okhttp-jvm` (670 zielonych testów, crash na starcie).

## Procedura

1. **Preflight** (nie wypisuje sekretów): `bash .claude/skills/verify-dev/scripts/preflight.sh`. Sprawdza `.env`, `secret.properties`, równość `MEILI_MASTER_KEY` ↔ `gamehive.search.api-key`, Docker i port 8080. Niczego nie naprawiaj w tych plikach sam — zgłoś użytkownikowi, co brakuje.
2. **Start**: `mvn spring-boot:run` z `run_in_background: true` (profil `dev` jest domyślny; `spring-boot-docker-compose` sam podnosi i zatrzymuje kontenery). Czekaj na linię `Started GameHiveApplication` w wyjściu albo w `logs/app.log` (Monitor z pętlą until, nie sleep).
3. **Sprawdź log startu** pod kątem:
   - Flyway: `Successfully applied N migrations` / `Schema ... is up to date`; `FlywayValidateException: Migration checksum mismatch` = ktoś edytował zastosowany `V*` → **nie** proponuj `flyway repair`; jedyna naprawa na dev to `docker compose down -v` (kasuje wolumen — **zapytaj użytkownika przed wykonaniem**), a w kodzie: przywrócić stary plik i dodać nowy `V*`;
   - Meili: brak ERROR od `MeiliIndexInitializer` (ustawienia indeksów zastosowane);
   - seedery: `DevDataInitializer` + `DevGameSeeder` przeszły; log wskazuje na reindeks.
4. **Token**: `TOKEN=$(bash .claude/skills/verify-dev/scripts/login.sh admin)` (john.doe, ADMIN) lub `user` (jane.smith, USER). Hasło obu: `password123`. Na dev **nie ma moderatora Marka** — to tylko fixture testowy; MODERATOR/ADMIN testuj adminem.
5. **Reindeks** (Meili po świeżym starcie jest pusty, seeder go nie zasila): `curl -sS -X POST localhost:8080/api/v1/admin/search/reindex -H "Authorization: Bearer $TOKEN"` → oczekiwane `{games:18, expansions:9, publishers:…, authors:…}`.
6. **Scenariusze pod zmianę** — wyprowadź z diffu (`git diff master...HEAD --stat`) i opisu issue. Każdy scenariusz: komenda curl + oczekiwany status/body + faktyczny wynik. Typowe:
   - nowa migracja → `docker exec gamehive-postgres psql -U gamehive -d gamehive -c '\d <tabela>'` (kolumny, FK z `ON DELETE`, indeksy), `select version, success from flyway_schema_history order by installed_rank;`;
   - indeks → zmiana treści APPROVED, potem `GET /api/v1/games/search?q=…`; log `Meili task N (...) succeeded` z wątku `search-index-1` z `correlationId` żądania, **po** linii `Handled request:`;
   - awaria Meili → `docker pause gamehive-meilisearch` (zawieszenie, nie odmowa połączenia): akcja moderatora ma wrócić 200 szybko, worker loguje ERROR ~10 s później; `docker unpause` na koniec **zawsze**;
   - Meili wprost: `curl -sS localhost:7700/indexes/gamehive_content/documents?limit=5 -H "Authorization: Bearer <MEILI_MASTER_KEY z .env>"` — klucza nie wypisuj w odpowiedzi;
   - błędy routingu/negocjacji (GH-140) → `curl -i -H 'Accept: application/xml' …` musi dać 406 z `ApiError`, nie 401.
7. **Pułapki przy ręcznym API**: `submit` w body gry/dodatku to prymitywny `boolean` — pominięty = 400 nawet na PUT. Fixture dev: 18 gier APPROVED + 3 zgłoszenia Jane (PENDING/REJECTED/DRAFT) + 12 dodatków; 8 gier bazowych ma dodatki i **nie da się ich skasować** (`GAME_HAS_EXPANSIONS`) — do testów delete bierz `Agricola`, `Azul`, `Splendor`, `Brass: Birmingham`, `Gloomhaven`, `Everdell`. Seeder jest idempotentny per tytuł, więc skasowana gra wraca po restarcie. `mvn compile` przy działającej aplikacji robi hot-restart (devtools) i ponownie uruchamia seeder.
8. **Sprzątanie**: zatrzymaj proces aplikacji (TaskStop na zadaniu w tle) — docker-compose zatrzyma kontenery sam (`lifecycle-management: start-and-stop`). Nie rób `docker compose down -v` bez zgody.

## Raport
Tabela: scenariusz → oczekiwane → faktyczne → ✅/❌, plus istotne linie logu. Jeśli weryfikacja coś potwierdziła, zaproponuj dopisanie wpisu "Verified end-to-end on `dev` (data)" do odpowiedniego skilla modułu lub CLAUDE.md (skill `claudemd-sync`), tak jak przy GH-122/GH-132.
