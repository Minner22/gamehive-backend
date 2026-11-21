---
name: claudemd-sync
description: Aktualizuje dokumentację i skille projektu GameHive po KAŻDYM zakończonym issue/tasku — decyduje, czy wpis trafia do CLAUDE.md (reguły przekrojowe), do istniejącego skilla eksperta/modułu (.claude/skills/*), czy zmiana jest na tyle duża, że wymaga NOWEGO skilla; weryfikuje fakty w kodzie, odhacza rozwiązane obserwacje [#issue] i trzyma gęsty styl "co + dlaczego". Użyj ZAWSZE i proaktywnie po zakończeniu implementacji issue (commit "GH-xxx dokumentacja: …"), po poprawkach po CR, gdy weryfikacja na dev coś potwierdziła, gdy użytkownik mówi "udokumentuj", "dopisz do CLAUDE.md", "zaktualizuj skille", oraz gdy w trakcie pracy wyjdzie nieoczywista pułapka.
---

# Synchronizacja dokumentacji i skilli

Użytkownik w pełni oddał Claude'owi edycję CLAUDE.md i skilli — **edytuj bezpośrednio, bez pytania o zgodę**. Kod aplikacji nadal pisze użytkownik.

**Utrzymanie skilli jest częścią „gotowe” każdego issue/taska** (reguła z CLAUDE.md, sekcja „Project skills”). Po zakończonej zmianie nie czekaj na prośbę — uruchom tę procedurę. Wynik ląduje w commicie `GH-<nr> dokumentacja: …` danego issue.

## Aktualizacja czy nowy skill?

**Aktualizuj istniejący skill**, gdy zmiana dotyka jego zakresu: nowa lub zmieniona klasa w mapie kodu, nowy endpoint, nowy `ErrorCode`, zmieniony przepływ albo niezmiennik, nowy test pinujący, zmiana fixture'ów, rozwiązana obserwacja.

**Utwórz nowy skill**, gdy spełniony jest choć jeden warunek:
- nowy moduł top-level (jak `collection/` w GH-121) → nowy skill ekspert z mapą kodu, przepływami, niezmiennikami i testami (wzór: `user-expert`);
- nowy podsystem z własnym cyklem życia: indeks, integracja zewnętrzna, proces w tle, nowy executor albo kolejka (jak Meili w GH-122) → skill ekspert podsystemu;
- notatki o module w istniejącym skillu przekroczyłyby ok. 1/3 jego objętości (sygnał, że to osobny temat);
- powtarzalny proces, który trzeba było przeprowadzić drugi raz ręcznie (np. nowy typ migracji danych, procedura wydania) → skill procesowy z listą kontrolną.

Nowy skill: katalog `.claude/skills/<nazwa>/SKILL.md`, frontmatter `name` + `description` (w `description` nazwy pakietów, ścieżki endpointów i słowa, po których ma się ładować), sekcje jak w istniejących ekspertach, a do tego wpis w indeksie CLAUDE.md („Project skills”) i w tabeli poniżej.

**Nie twórz skilla** dla jednorazowej poprawki, zmiany jednej klasy albo czegoś, co mieści się w trzech linijkach istniejącego skilla.

## Gdzie co trafia

| Treść | Miejsce |
|---|---|
| Reguła obowiązująca w wielu modułach (konwencja błędów, wzorzec AFTER_COMMIT, "zielony test nie dowodzi X") | `CLAUDE.md` |
| Komendy, układ modułów, bezpieczeństwo/JWT, audyt użytkowników, persystencja, CI | `CLAUDE.md` |
| Szczegóły modułu `game/` (poza search) | `.claude/skills/moderation-workflow/SKILL.md` |
| Szczegóły `collection/` | `.claude/skills/collections/SKILL.md` |
| Szczegóły `game/search/`, Meili, executor indeksu | `.claude/skills/search-meili/SKILL.md` |
| Mapa kodu, przepływy i obserwacje `auth/`, `user/`, `common/` + `config/` | `.claude/skills/{auth,user,common}-expert/SKILL.md` (zasady przekrojowe tych modułów zostają w CLAUDE.md) |
| Nowy wzorzec lub pułapka testowa | `.claude/skills/testing-expert/SKILL.md` |
| Obserwacja z analizy, która została naprawiona | usuń ją z sekcji "Obserwacje" skilla eksperta i opisz rozwiązanie w odpowiednim miejscu |
| Zmiana niezmienników `data.sql` | tabela w `.claude/skills/fixture-guard/SKILL.md` **oraz** notatka w skillu modułu |
| Nowa konwencja API/błędów | CLAUDE.md (uzasadnienie) + punkt w `.claude/skills/api-conventions/SKILL.md` |
| Nowy moduł top-level | wpis w "Module layout" w CLAUDE.md + nowy skill wiedzy w tym samym formacie (frontmatter `description` z nazwą pakietu i ścieżkami endpointów, żeby skill się ładował) |

Gdy szczegół modułu staje się regułą przekrojową, dopisz jedną linię do streszczenia modułu w CLAUDE.md. **CLAUDE.md ma pozostać mały** (ok. 30 KB po wydzieleniu skilli) — nie wklejaj tam znowu szczegółów modułów.

## Styl (zachowaj istniejący)
- Angielski, gęsty, jeden punkt = jedna decyzja. Format: **co** (pogrubione) → **dlaczego** → **konsekwencja / czego nie "naprawiać"**.
- Odwołania do issue (`GH-<nr>`) i konkretnych nazw (`klasa`, `metoda`, `ErrorCode`, plik migracji).
- Zapisuj odstępstwa od tekstu issue i odrzucone alternatywy — to najcenniejsza część ("deliberately not…", "do not 'fix' it back").
- Pułapki pinowane testem: nazwij test, który je pinuje.
- Wpisy "Verified end-to-end on `dev` (data RRRR-MM-DD)" tylko po faktycznej weryfikacji — z wersjami infrastruktury i konkretnymi obserwacjami.
- Daty bezwzględne, nigdy "wczoraj"/"w zeszłym tygodniu".

## Procedura
1. `git diff master...HEAD --stat` + przeczytanie zmienionych plików. **Każdy dokumentowany fakt sprawdź w kodzie** (nazwa klasy, wartość domyślna, kod HTTP) — nie dokumentuj planu, tylko to, co jest.
2. Znajdź wpisy, które zmiana dezaktualizuje (`Grep` po nazwach klas/kodów w CLAUDE.md i `.claude/skills/`), i popraw je — nieaktualna dokumentacja jest gorsza niż brak.
3. Dopisz nowe wpisy we właściwym miejscu według tabeli.
4. Gdy zmienił się `data.sql` → zaktualizuj tabelę w `fixture-guard`.
5. Obserwacje oznaczone `[#nr]`, które to issue rozwiązało → usuń z sekcji „Obserwacje” skilla eksperta; decyzję i rozwiązanie opisz w odpowiednim miejscu skilla (albo w CLAUDE.md, jeśli są przekrojowe).
6. Rozstrzygnij „aktualizacja czy nowy skill” (kryteria wyżej); przy nowym skillu dopisz go do indeksu w CLAUDE.md.
7. Pokaż użytkownikowi krótkie podsumowanie: które pliki dokumentacji i skille zmieniono lub utworzono, i dlaczego.
