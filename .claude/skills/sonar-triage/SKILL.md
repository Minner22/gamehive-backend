---
name: sonar-triage
description: Pobiera otwarte issues i stan quality gate projektu GameHive z SonarCloud (projekt Minner22_gamehive-backend), grupuje je po regułach i proponuje plan sprzątania — z rozróżnieniem na prawdziwe problemy, świadome decyzje (Won't fix) i fałszywe alarmy. Użyj, gdy użytkownik pyta o Sonara, dług techniczny, quality gate, code smells, pokrycie, "co pokazuje SonarCloud", albo gdy CI na PR jest czerwone z powodu analizy.
---

# Triage SonarCloud

Projekt: `Minner22_gamehive-backend` (analiza w jobie `sonar` workflow `CI`, `.github/workflows/ci.yml` — na każdym PR i pushu do `master`, na artefakcie z joba `test`; pomijana dla Dependabota i forków, GH-153). Wygasły `SONAR_TOKEN` czerwieni tylko job `sonar` (`HTTP 403`), nie `test`. Precedens: GH-136 (sprzątanie długu).

## Pobranie danych
API SonarCloud. **Projekt jest prywatny** — anonimowe zapytanie zwraca `{"errors":[{"msg":"Project doesn't exist"}]}` (sprawdzone 2026-11-21). Potrzebny token: poproś użytkownika o wygenerowanie go w SonarCloud (My Account → Security) i ustawienie zmiennej `SONAR_TOKEN` w środowisku, z którego startuje Claude Code. Do każdego `curl` dodaj `-u "$SONAR_TOKEN:"`. Jeśli zmiennej brak, zatrzymaj się i poproś o nią. **Nigdy nie proś o wklejenie tokena do czatu.**

```bash
B=https://sonarcloud.io/api; P=Minner22_gamehive-backend; A=(-u "$SONAR_TOKEN:")
# quality gate
curl -sS "${A[@]}" "$B/qualitygates/project_status?projectKey=$P"
# otwarte issues (max 500 na stronę; &p=2 itd.); dla PR dodaj &pullRequest=<nr>
curl -sS "${A[@]}" "$B/issues/search?componentKeys=$P&resolved=false&ps=500&facets=rules,severities,types"
# security hotspots
curl -sS "${A[@]}" "$B/hotspots/search?projectKey=$P&status=TO_REVIEW&ps=500"
# metryki
curl -sS "${A[@]}" "$B/measures/component?component=$P&metricKeys=coverage,bugs,vulnerabilities,code_smells,duplicated_lines_density,sqale_index"
```

Nie ma `jq` — parsuj JSON przez `node -e` (Node jest zainstalowany) albo PowerShell `ConvertFrom-Json`. Wynik zapisuj w scratchpadzie, nie w repo.

## Klasyfikacja
Dla każdej reguły (po facecie `rules`): liczba, przykładowe `plik:linia`, a potem jedna z kategorii:

- **Naprawić** — prawdziwy problem.
- **Świadoma decyzja — Won't fix w SonarCloud** (oznacza użytkownik w UI; nie "naprawiaj" w kodzie):
  - `java:S1133` na celowo zdeprecjonowanych endpointach taksonomii (GH-131);
  - wszystko, co CLAUDE.md lub skill modułu opisuje jako "deliberately"/"do not fix" — przed zaklasyfikowaniem jako bug sprawdź dokumentację (`Grep` po nazwie klasy w CLAUDE.md i `.claude/skills/`).
- **Już rozwiązane w projekcie — pilnować, by nie wróciło**: S1710 (wrapper `@ApiResponses`), S5778 (więcej niż jedno wywołanie mogące rzucić w lambdzie `assertThrows`), S5976 (parametryzacja podobnych testów), S6213 (nazwa `record` jako identyfikator), S1123 (`@deprecated` w Javadoc).
- **Fałszywy alarm** — z uzasadnieniem.

Pokrycie: wykluczenia Sonara to pakiety `dto`, `model`, `config`, `mapper`, `exception`, `logging` i `GameHiveApplication` — niskie pokrycie tam nie jest problemem.

## Wynik
Tabela reguła → liczba → kategoria → proponowane działanie. Do tego propozycja jednego issue "sprzątanie" w stylu GH-136: jeden commit na regułę, `GH-<nr> S<reguła>: <opis>`. Zgodnie z zasadami użytkownika — **plan, nie poprawki**: kod zmienia użytkownik.
