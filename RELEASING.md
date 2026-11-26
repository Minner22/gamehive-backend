# Wydawanie nowej wersji GameHive (backend)

## Schemat wersji

[SemVer](https://semver.org/lang/pl/): `MAJOR.MINOR.PATCH`

- **MINOR** (`0.1.0` → `0.2.0`): nowe funkcje widoczne dla klienta API (frontu),
- **PATCH** (`0.2.0` → `0.2.1`): tylko poprawki błędów,
- **MAJOR**: `1.0.0` = pierwsza wersja wdrożona produkcyjnie (#49). Wersje `0.x` to wersje rozwojowe — zmiana łamiąca
  kontrakt API podnosi w nich MINOR, nie MAJOR.

Wydane wersje:

| Wersja | Zawartość |
|---|---|
| [`0.1.0`](https://github.com/Minner22/gamehive-backend/releases/tag/v0.1.0) | konta i autoryzacja (JWT, rotacja refresh tokenów), panel admina z audytem, biblioteka gier z moderacją, dodatki, kolekcje, wyszukiwanie pełnotekstowe (Meilisearch), CI/CD z obrazem Docker w GHCR |

## Wersja w budowie: `-SNAPSHOT`

- `master` ma **zawsze** wersję z dopiskiem `-SNAPSHOT`, np. `0.2.0-SNAPSHOT` = „przyszła wersja 0.2.0, jeszcze niewydana”.
- Kolejne PR-y nie zmieniają wersji. Zmienia ją tylko proces wydania (krok „Po wydaniu”).
- **Tag obrazu Docker pochodzi z taga git, nie z `pom.xml`** — job `image` publikuje `:X.Y.Z` dla taga `vX.Y.Z`
  niezależnie od wersji w pomie. Wersja w pomie wpływa tylko na lokalny `./mvnw spring-boot:build-image`.

## Przed wydaniem

- [ ] zadania planowane na tę wersję są zamknięte (milestone na GitHubie),
- [ ] ostatni run `CI` na `master` jest zielony (plakietka w README / zakładka *Actions*),
- [ ] lokalnie: `git switch master`, `git pull`, `git status` bez zmian,
- [ ] zmiany niewidoczne dla suity (migracje na istniejących danych, Meilisearch, praca w kontenerze) sprawdzone
  na `dev` / w obrazie,
- [ ] ✅ przy wydawanej wersji w tabeli „Wydane wersje” wyżej i w sekcji „Roadmap” w `README.md` oraz „Roadmapa”
  w `README.pl.md` — to zwykły PR **przed** tagiem.

## Wydanie krok po kroku

```bash
git switch master && git pull
gh run list --branch master --workflow CI --limit 1      # ostatni run ma być "success"

# tag + wydanie na GitHubie z notatkami wygenerowanymi z PR-ów od poprzedniego taga
gh release create vX.Y.Z --target master --title "vX.Y.Z" --generate-notes
```

Albo ręcznie, bez wydania na GitHubie (sam tag z adnotacją — niesie autora, datę i opis):

```bash
git tag -a vX.Y.Z -m "GameHive backend X.Y.Z"
git push origin vX.Y.Z
```

> W PowerShellu argumenty `-D…` dla Mavena muszą być w cudzysłowach (`"-Dkey=1.2.3"`) — inaczej PowerShell
> rozcina je na kropce. Komendy wyżej ich nie używają.

## Co robi CI na tagu

Push taga `v*` uruchamia workflow `CI`:

- `test` i `integration` — ponownie, na commicie wydania,
- `image` — buduje obraz i publikuje `ghcr.io/minner22/gamehive-backend:X.Y.Z` (prefiks `v` obcięty), `:latest`
  oraz `:sha-<7>`; lista tagów jest w *job summary*,
- `sonar` jest pomijany (commit był już analizowany na `master`), `migrations-guard` i `dependency-review` działają tylko na PR.

Sprawdzenie: zakładka *Actions* (run na tagu zielony) i
[pakiet w GHCR](https://github.com/Minner22/gamehive-backend/pkgs/container/gamehive-backend) z nowymi tagami.
Buildpacki dają powtarzalne buildy, więc `:X.Y.Z` ma ten sam digest co `:sha-<7>` zbudowany z tego commita na `master`.

## Po wydaniu

- PR podbijający wersję w `pom.xml` na następną `-SNAPSHOT` (np. `0.2.0` → `0.3.0-SNAPSHOT`), żeby lokalny
  `build-image` nie udawał wydanej wersji,
- zamknięcie milestone'u.

## Czego nie robić

- **Nie przesuwaj ani nie usuwaj opublikowanego taga** — obraz `:X.Y.Z` mógł już zostać pobrany. Błąd naprawia się
  kolejną wersją (`vX.Y.Z+1`).
- Nie wydawaj z gałęzi innej niż `master` ani z commita, którego run `CI` na `master` nie był zielony.

Tagi nie są chronione przez ruleset `master` (ten dotyczy tylko gałęzi). Ruleset na `refs/tags/v*` (zakaz usuwania
i nadpisywania) byłby osobną decyzją.
