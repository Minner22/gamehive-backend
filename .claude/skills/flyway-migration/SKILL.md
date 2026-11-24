---
name: flyway-migration
description: Reguły i lista kontrolna dla nowej migracji Flyway w GameHive (src/main/resources/db/migration/V*__*.sql) oraz każdej zmiany encji JPA, która zmienia schemat — numeracja, zakaz edycji zastosowanych plików, FK/ON DELETE vs @OnDelete, indeksy na FK, rozjazd H2 vs PostgreSQL, weryfikacja na dev. Użyj ZAWSZE, gdy plan lub zmiana dodaje/zmienia tabelę, kolumnę, constraint albo indeks, zmienia adnotacje mapowania encji (@Column, @ManyToOne, @JoinTable…), albo gdy ktoś chce "poprawić" istniejący plik V*.
---

# Migracje Flyway

## Twarde reguły
1. **Nigdy nie edytuj pliku `V*`, który mógł być już zastosowany** (czyli każdego, który jest na `master` albo był uruchomiony na dev). Flyway trzyma checksumy i wtedy nie wstaje (`Migration checksum mismatch`). Precedens: `d7a981d` dopisał `CREATE INDEX` do zastosowanego `V8` → dev stał na V8 przez 5 tygodni bez V9–V11. Napraw to nowym plikiem `V<n+1>`. Wyjątek: plik dodany na bieżącej gałęzi i jeszcze nigdzie nieuruchomiony.
2. **`flyway repair` nie jest naprawą** — przepisuje tylko checksum, a dopisane instrukcje zostają na zawsze niewykonane. Na dev ratunkiem jest `docker compose down -v` (kasuje wolumen; **zawsze pytaj użytkownika**).
3. **Zielony lokalny `./mvnw test` nie dowodzi niczego o migracji** — profil `test` ma Flyway wyłączony, a schemat H2 generuje Hibernate z encji. **Dowodzi tego CI (GH-154)**: job `integration` stosuje wszystkie `V*` na pustym PostgreSQL 16 i robi `ddl-auto=validate`, a job `migrations-guard` blokuje edycję, usunięcie i zmianę nazwy istniejącego `V*` na PR. Lokalnie to samo: `./mvnw -Pintegration test` (Docker Desktop).
4. Boot 4: Flyway działa tylko z `spring-boot-starter-flyway`; samo `flyway-core` po cichu nic nie robi.

## Lista kontrolna nowego pliku
- [ ] Numer: kolejny po najwyższym w `src/main/resources/db/migration` (sprawdź `ls`, nie zgaduj; ostatni znany: `V12`). Nazwa `V<n>__snake_case_opis.sql`.
- [ ] Nagłówek komentarza po polsku: `-- GH-<nr> (G<krok>): co i dlaczego`, wraz z decyzjami dotyczącymi FK i kaskad (wzór: `V12`).
- [ ] Tabela encji `LongEntity`: `id BIGSERIAL PRIMARY KEY`, `created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL`, `updated_at TIMESTAMP WITH TIME ZONE`. Encja `UuidEntity`: `id uuid` (generowany w aplikacji, v7).
- [ ] `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`, jak w istniejących plikach.
- [ ] Odwołanie do użytkownika: `uuid NOT NULL` **bez FK** do `application_users` (konwencja: audyty, `submitted_by`, kolekcje) + sprzątanie w aplikacji, jeśli dane są per-user.
- [ ] Każde FK ma świadomie wybrane `ON DELETE`:
  - `RESTRICT` (domyślne) + guard w serwisie (`*_IN_USE`, `GAME_HAS_EXPANSIONS`) — gdy kasowanie rodzica ma być blokowane;
  - `CASCADE` — gdy wiersz nie ma sensu bez rodzica. **Dodaj też `@OnDelete(action = OnDeleteAction.CASCADE)` na `@ManyToOne`**, inaczej H2 w testach nie ma kaskady i testy kłamią (wzór V12). Na `@ManyToMany` z join table właścicielem jest encja — JPA czyści wiersze sam, SQL cascade jest tylko dla PG.
- [ ] **Indeks na każdej kolumnie FK**, po której się filtruje lub która jest stroną RESTRICT — Postgres nie indeksuje FK automatycznie (konwencja `idx_<tabela>_<kolumna>`).
- [ ] Unikalność → nazwany constraint `uq_<…>` (wyścig find-or-create zamieni się w 409 `DATA_CONFLICT`).
- [ ] Nowa kolumna `NOT NULL` w istniejącej tabeli → `DEFAULT` albo backfill w tej samej migracji (wzór `V9` autorzy → APPROVED).
- [ ] Seed danych słownikowych w migracji (wzór `V2`, `V7`); dane deweloperskie **nie** — od tego jest `DevGameSeeder`.

## Spójność encja ↔ migracja (bo H2 bierze schemat z encji)
- [ ] Nazwy tabel/kolumn/join table w `@Table`/`@Column`/`@JoinTable` dokładnie jak w SQL (join table w liczbie pojedynczej: `game_category`).
- [ ] `nullable`, `length`, `unique`, `updatable = false` na encji odpowiadają SQL.
- [ ] DEFAULT-y z SQL **nie istnieją w H2** → fixture'y w `data.sql` muszą podawać te kolumny jawnie (skill `fixture-guard`).
- [ ] Indeksy i `ON DELETE` (poza `@OnDelete`) nie istnieją w H2 — testy repozytorium weryfikują zachowanie JPA, nie SQL.

## Co sprawdza CI (GH-154), a co nadal `dev`
- `migration/FlywayPostgresMigrationTest` (`@Tag("postgres")`, profil `test` + `it-postgres`, Testcontainers): kontekst wstaje = migracje od zera + `validate`; asercje FK `ON DELETE` i indeksów. **Nowe FK z wybraną regułą albo nowy indeks → dopisz wiersz do `@CsvSource`** (`foreignKeyDeleteRules` / `indexesExist`), inaczej regułę pilnuje tylko ręczna weryfikacja.
- **Obraz w teście = obraz w `docker-compose.yml`** (`postgres:16-alpine`) — przy podbiciu PostgreSQL zmień oba.
- `validate` sprawdza istnienie tabel/kolumn i zgodność typów, ale **nie** `nullable`, `length` ani DEFAULT-y — te punkty listy kontrolnej zostają ręczne.
- Pole w encji bez migracji: H2 utworzy kolumnę sam (lokalne testy zielone), job `integration` czerwony z `Schema validation: missing column` — to dokładnie przypadek, który CI ma łapać.

## Weryfikacja na dev (skill `verify-dev`) — dane seedera, cały stos
- log: `Successfully applied 1 migration … v<n>`;
- `docker exec gamehive-postgres psql -U gamehive -d gamehive -c '\d+ <tabela>'` — kolumny, FK z `ON DELETE`, indeksy;
- `select version, description, success from flyway_schema_history order by installed_rank;`;
- dla RESTRICT: próba `DELETE` rodzica z dzieckiem musi odbić się od FK (jak w weryfikacji V11 z 2026-08-06).
