---
name: plan-issue
description: Tworzy plan implementacji issue GameHive (GH-xxx) dopasowany do konwencji projektu — pliki, ErrorCode z sekcją, wpływ na data.sql, migracje, testy (w tym AFTER_COMMIT), OpenAPI, weryfikacja na dev — i czeka na akceptację. Użyj, gdy użytkownik podaje numer issue ("GH-142", "#142", "zróbmy issue 142"), wskazuje plik z docs/issues, prosi o plan nowego feature'a lub pyta "jak to ugryźć". Nie pisze kodu aplikacji.
---

# Plan issue

## Zasady pracy z tym użytkownikiem (z pamięci — obowiązują bezwzględnie)
- **Najpierw plan, potem akceptacja** ("tak") — dopiero potem cokolwiek dalej.
- **Użytkownik koduje sam kod produkcyjny, Claude pisze testy** (od GH-142). Plan ma postać **kroków per commit**: „Ty:” — co i jak zaimplementować (krótkie fragmenty tam, gdzie pomagają, nie gotowa implementacja), „Claude (testy):” — przypadki testowe, które Claude dopisze po zgłoszeniu ukończenia kroku i uruchomi przed commitem, oraz **„Commit:” — proponowany commit message** (`GH-<nr> <opis po polsku>`) z listą plików do `git add`. Po zielonych testach danego kroku Claude podaje gotowy commit message jeszcze raz, z faktyczną listą plików. CLAUDE.md i skille w `.claude/skills/` Claude edytuje sam (carve-out, ostatni commit `GH-<nr> dokumentacja: …`).
- Komunikacja po polsku; identyfikatory i nazwy techniczne w oryginale.

## Kroki

1. **Źródło issue.** `gh issue view <nr> --comments`, a jeśli to krok roadmapy gier — także `docs/issues/games/G*.md` i `docs/roadmap-games.md`. Zauważ rozbieżności między tekstem issue a stanem kodu. Projekt ma tradycję świadomych odstępstw od literalnego tekstu issue (np. `/my` zamiast `/mine`, `REINDEX_ALREADY_RUNNING` w Application zamiast Domain, moderator-edit pod `/moderation/games/{id}` zamiast `/games/{id}`). Każde odstępstwo nazwij i uzasadnij w planie.
2. **Załaduj wiedzę modułu**: skill `moderation-workflow` (game/), `collections` (collection/), `search-meili` (game/search/). Przeczytaj faktyczny kod, którego plan dotyczy — nie planuj z pamięci.
3. **Gałąź**: konwencja `GH-<nr>-<slug-po-polsku-z-myślnikami>` (np. `GH-140-nieobslugiwana-metoda-http-i-nieistniejaca-sciezka`), od aktualnego `master`.
4. **Napisz plan** w tej strukturze:

   - **Cel i odstępstwa od tekstu issue**
   - **Zmiany** (plik → co i dlaczego), pogrupowane w commity. Konwencja commitów: `GH-<nr> <opis po polsku>`, osobny commit `GH-<nr> dokumentacja: CLAUDE.md`, po review `GH-<nr> poprawki po CR`.
   - **Kontrakt API** — endpointy, statusy, body (skill `api-conventions`)
   - **ErrorCode** — nowe/reużyte, sekcja (Domain/Application/Infrastructure) + uzasadnienie, `HttpStatus`
   - **Persystencja** — nowa migracja `V<n>`? (skill `flyway-migration`); zmiany encji
   - **data.sql** — czy trzeba dotknąć? (skill `fixture-guard` — przeprowadź analizę TERAZ, nie w trakcie)
   - **Testy** — klasy i przypadki; które muszą być bez `@Transactional` (AFTER_COMMIT → własne wiersze + `@AfterEach`); co pinuje `OpenApiDocumentationTest`
   - **Czego `mvn test` NIE dowiedzie** → scenariusze dla `verify-dev`
   - **Dokumentacja i skille** — co trafi do CLAUDE.md, które skille trzeba zaktualizować (mapa kodu, niezmienniki, testy, obserwacje `[#nr]` do odhaczenia) i czy zmiana zasługuje na **nowy** skill (kryteria w `claudemd-sync`). Wykonuje to Claude po zakończeniu issue, w commicie `GH-<nr> dokumentacja: …`
   - **Ryzyka i pytania otwarte**

5. **Lista kontrolna przed oddaniem planu** (wypisz tylko to, co dotyczy):
   - enumeration-safe 404 vs świadomy wyciek istnienia id (precedensy: #120, #121);
   - nowy zapis do treści APPROVED → zdarzenie indeksu Meili;
   - akcja moderacyjna/kontowa → zdarzenie audytu (AFTER_COMMIT → `REQUIRES_NEW` w osobnym beanie);
   - pole/relacja do użytkownika → UUID bez FK + sprzątanie po `UserDeletedEvent`, jeśli dane są per-user;
   - kasowanie → guard w serwisie + `RESTRICT` w DB jako druga linia obrony;
   - side effect po commicie wymaga, żeby metoda serwisu była `@Transactional` (inaczej AFTER_COMMIT nie odpali);
   - paginacja → `@PageableDefault(sort = "id", direction = DESC)`, dedykowany `Page*Dto`;
   - parametry liczbowe → clamp, nie `@Max`;
   - zmiana kształtu odpowiedzi istniejącego endpointu → oznacz wprost jako **breaking change**.
6. **Zatrzymaj się** i poproś o akceptację. Po akceptacji nie implementuj — pomagaj, gdy użytkownik zapyta. Na koniec zaproponuj `/code-review`, `/security-review` (gdy dotyka auth/uprawnień) i `verify-dev`.
