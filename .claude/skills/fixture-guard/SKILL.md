---
name: fixture-guard
description: Strażnik wspólnych fixture'ów testowych GameHive (src/test/resources/data.sql) — przed każdą zmianą data.sql lub dodaniem testu zakładającego konkretny stan fixture'ów wypisuje niezmienniki i asercje (containsExactly/totalElements), które zmiana złamie. Użyj ZAWSZE, gdy plan lub zmiana dotyka data.sql albo SeededUsers, gdy test ma tworzyć gry/dodatki/autorów/kolekcje w stanie PENDING/APPROVED, albo gdy po zmianie padają niezwiązane testy z asercjami na listach.
---

# Fixture guard — `data.sql`

`data.sql` ładuje się **raz na całą instancję H2** i jest wspólny dla wszystkich klas testowych. Ok. 115 asercji (`containsExactly`, `totalElements`, `hasSize`) zakłada jego dokładną zawartość — jeden nowy wiersz potrafi wywalić testy w zupełnie innym module. Historia i uzasadnienia poszczególnych wierszy: skille `moderation-workflow`, `collections`, `search-meili`.

## Niezmienniki (stan na GH-140)

**Użytkownicy** (`support/SeededUsers`): John `…0001` ADMIN, Jane `…0002` USER, Mark `…0003` MODERATOR, `UNKNOWN_ID` `…00ff`. Hasło `password123`. Nie kasuj seedowanego użytkownika w teście non-`@Transactional` — utwórz własnego (wzór: `CollectionUserCleanupTest`).

**Gry** (id = kolejność insertów):

| id | tytuł | właściciel | status | dlaczego tak |
|---|---|---|---|---|
| 1 | Agricola | Jane | APPROVED | hard-delete w `GameModerationControllerTest.delete_approved_204` → **nie może mieć dodatków** |
| 2 | Pandemic | Jane | PENDING | **jedyna PENDING** — `GameRepositoryTest` `containsExactly("Pandemic")`; testy potrzebujące PENDING tworzą własną przez `POST /api/v1/games` |
| 3 | Odrzucona Gra | John | REJECTED, count=1 | "cudze zgłoszenie" |
| 4 | Szkic Jane | Jane | DRAFT | testy "DRAFT nie wycieka" |
| 5 | Odrzucona Jane | Jane | REJECTED, count=1 | |
| 6 | Limit Jane | Jane | REJECTED, count=2 | **dokładnie na limicie testowym** (`max-resubmissions: 2`) |
| 7 | Carcassonne | John | APPROVED | baza wszystkich dodatków; atrybuty dobrane tak, by **nie pasować do żadnego filtra** biblioteki (publisher 2, category 2, mechanic 3, 2..2 graczy, rok 2000) |

Biblioteka = gry 1 + 7 (`totalElements = 2`).

**Dodatki** (wszystkie na grze 7): 1 APPROVED (nadpisuje `maxPlayers` + kategorie, reszta dziedziczona; hard-delete w testach), 2 PENDING — **jedyny PENDING** (`containsExactly` w `GameExpansionRepositoryTest`), 3 DRAFT, 4 REJECTED count=1, 5 REJECTED count=2 (limit), 6 DRAFT **Johna** ("cudze"). 1–5 należą do Jane.

**Taksonomia**:
- kategorie: 1 Strategy, 2 Family, **3 Party — niepowiązana z niczym** (`deleteCategory_asAdmin_204`), 4 Cooperative, **5 Expansion Only — tylko dodatek 1** (guard `CATEGORY_IN_USE` po stronie dodatków);
- mechaniki 1–4;
- wydawcy: **wszyscy trzej powiązani z grami** (testy delete muszą tworzyć własnego); wszyscy zawierają "Games" (`q=games&limit=2` dowodzi limitu); `Pending Games` PENDING;
- autorzy: 1 Uwe Rosenberg, 2 Reiner Knizia (**niepowiązany** — cel delete), 3 Oczekujacy Autor — **jedyny PENDING i niepowiązany**; nie dodawaj autora PENDING (`AuthorRepositoryTest` `containsExactly("Autor")`); trójka pokrywa dopasowanie po imieniu / nazwisku / pełnej frazie.

**Kolekcje**: Jane ma grę 1 i dodatek 1 (celowo — kaskada przy ich hard-delete), John ma grę 7, **Mark ma pustą kolekcję** (to on dodaje dodatek 1 bez posiadania gry bazowej). Jane celowo **nie** ma gry 7.

**Wymogi techniczne H2**: schemat generuje Hibernate (bez DEFAULT-ów z migracji) → gry muszą mieć jawne `resubmission_count`. `created_at` jest NULL wszędzie poza kolekcjami (DTO wystawia `addedAt`) — nie sortuj ani nie asertuj go na wierszach seedowanych.

## Procedura przy planowanej zmianie

1. **Domyślna odpowiedź: test tworzy własne dane** zamiast zmieniać `data.sql`. Test `@Transactional` → rollback za darmo. Test non-`@Transactional` (AFTER_COMMIT) → śledzone id + `@AfterEach` (dodatki przed grami, cleanup w `TransactionTemplate`, `existsById` przed delete). GH-118, 119, 122, 131 i 138 obeszły się **bez** zmiany `data.sql`.
2. Jeśli zmiana jest konieczna, znajdź zależne asercje:
   - `Grep` w `src/test` po `containsExactly|totalElements|hasSize|isEmpty\(\)` oraz po tytułach/nazwach/id, których dotyczy zmiana;
   - dla gier/dodatków także po `findByModerationStatus|status=PENDING|status=REJECTED` i testach biblioteki/wyszukiwarki;
   - sprawdź, czy nowy wiersz nie pasuje przypadkiem do istniejących filtrów (wzór: dobór atrybutów Carcassonne);
   - zapytania z sortem `id DESC` i domyślnym `size` mogą wypchnąć stare fixture'y poza pierwszą stronę (stąd `size=50` w testach `?status=REJECTED`).
3. Raport: zmiana → złamane niezmienniki → testy do aktualizacji (`plik:linia`) → alternatywa bez zmiany `data.sql`. Każdą zmianę asercji uzasadnij (wzór GH-120: "zmieniła się dokładnie jedna asercja, `totalElements` 1 → 2").
4. Po zmianie: **pełny** `mvn test` (zależności są międzyklasowe), potem aktualizacja tej tabeli i skilla modułu (skill `claudemd-sync`).
