---
name: api-conventions
description: Lista kontrolna konwencji REST/OpenAPI/obsługi błędów GameHive przy dodawaniu lub zmianie endpointu, kontrolera, DTO, ErrorCode albo handlera w GlobalExceptionHandler — @Tag/@SecurityRequirement/@ApiResponse, Page*Dto, operationId, paginacja, clamp vs @Max, enumeration-safe 404, sekcje ErrorCode, statyczne komunikaty, deprecacja, OpenApiDocumentationTest. Użyj przy planowaniu i review każdej zmiany w warstwie controller/dto/exception oraz gdy użytkownik pyta "jaki kod błędu", "gdzie dać ten ErrorCode", "jak udokumentować endpoint".
---

# Konwencje API

Uzasadnienia: sekcje "Error handling" i "API documentation (OpenAPI/Swagger)" w CLAUDE.md. Tu jest lista kontrolna — przy review wypisz tylko punkty, które zmiana łamie.

## Kontroler i OpenAPI
- [ ] `@Tag` na klasie; zabezpieczony kontroler → klasowe `@SecurityRequirement(name = "bearerAuth")`.
- [ ] Role przez `@PreAuthorize` (moderacja: `hasAnyRole('MODERATOR','ADMIN')`). `SecurityConfig` nie ma reguł dla `/api/v1/admin/**` — adnotacja jest jedyną ochroną. Anonim = **401**, zła rola = **403**.
- [ ] `@Operation` + **powtarzalne `@ApiResponse`** — nigdy wrapper `@ApiResponses` (Sonar S1710, usunięte w GH-136). Wspólne 401/403/500 raz na poziomie klasy (wzór `AdminUserController`).
- [ ] Body błędu: `@Schema(implementation = ApiError.class)`, dla 400 `ApiValidationError.class`.
- [ ] **Jawne `operationId`**, gdy nazwa metody trafia do generowanego klienta (GH-138: rename metody po cichu przemianował klienta, duplikat dostał sufiks `_1`).
- [ ] Odpowiedź stronicowana: `Page<T>` w kodzie, a w `@ApiResponse` dedykowany płaski `Page<Nazwa>Dto` (record w `dto`).
- [ ] Sukces to record (`MessageResponseDto`, `AccessTokenResponseDto`…), nie `Map`/`String`. DTO = record z `@Schema` na komponentach.
- [ ] Nowa ścieżka / zmiana kontraktu → asercja w `OpenApiDocumentationTest`.
- [ ] Deprecacja = trzy rzeczy: `@Deprecated(since = "GH-<nr>")`, `@Operation(deprecated = true, description = co zamiast i co traci wywołujący)`, Javadoc `@deprecated {@link następca}` (S1123). S1133 = Won't fix.

## Parametry i paginacja
- [ ] `@PageableDefault(sort = "id", direction = Sort.Direction.DESC)` — nie `createdAt` (NULL w fixture'ach H2). Wyjątek: wyszukiwanie po trafności (bez sortu).
- [ ] Limity liczbowe **clampuj** (`Math.clamp`), nie `@Max`/`@Min` na `@RequestParam` — brak handlera `ConstraintViolationException` = 500.
- [ ] Filtr enum: typ z **samymi legalnymi wartościami** (wzór `ModerationQueueStatus`) — zła wartość = 400 `VALIDATION_ERROR` z bindingu, bez nowego `ErrorCode`.
- [ ] Filtry: osobne `@RequestParam` → record filtra → `Specification` z helperami `common/persistence/Specifications` (`equalsIfPresent`, `joinEqualsIfPresent`, `likeIgnoreCaseIfPresent` z escapowaniem `%`/`_`/`\`).
- [ ] Tożsamość wyłącznie z tokena: `new Email(authentication.getName())` → UUID w serwisie. Żaden path/param nie niesie `userId` bieżącego użytkownika.
- [ ] Serwis zwraca DTO, gdy mapowanie dotyka LAZY kolekcji (mapowanie wewnątrz `@Transactional`, bez OSIV).

## Błędy
- [ ] Sekcja `ErrorCode` = klasa wyjątku = poziom logu:
  - **Domain** → `DomainException` (INFO): reguła biznesowa / guard stanu (`GAME_NOT_PENDING`, `*_IN_USE`, `*_REQUIRED`);
  - **Application** → `ApplicationException` (WARN): lookup miss, ważność tokena, precondition use-case'u (`*_NOT_FOUND`, `REINDEX_ALREADY_RUNNING`);
  - **Infrastructure** → `InfrastructureException` (ERROR + stack): zawsze opakowuje złapany wyjątek zewnętrzny;
  - **Handler-only** → nigdy nie rzucane, tylko `GlobalExceptionHandler`.
- [ ] Wpis alfabetycznie w swojej sekcji, z `HttpStatus` i sensownym `defaultMessage` po angielsku — **to jedyny tekst, jaki zobaczy klient**: od GH-149 `ApiError.message` = `ErrorCode.defaultMessage` zawsze, niezależnie od komunikatu wyjątku.
- [ ] Komunikat w `throw new …Exception(CODE, "…")` to **szczegół do logu** (nigdy nie trafia do odpowiedzi) — wolno w nim podać kontekst (id, typ tokena, komunikat biblioteki), ale e-maile tylko zamaskowane (`Email.obfuscated()` / `LoggingUtils.obfuscateEmail`). Tekst dla użytkownika robi front, mapując `errorCode` (gamehive-frontend#81). Nie buduj `ApiError` z `ex.getMessage()` w nowym handlerze — tylko `error(ErrorCode)`.
- [ ] Najpierw sprawdź, czy istniejący kod pasuje (reuse `GAME_NOT_APPROVED` w #121), ale **nie** pożyczaj kodu, który kłamie o typie zasobu (dodatki mają własne `EXPANSION_*`).
- [ ] Odczyt cudzego niepublicznego zasobu = ten sam `*_NOT_FOUND` co brak (enumeration-safe). Świadomy wyciek istnienia id opisz w dokumentacji jako "known, accepted asymmetry".
- [ ] Walidacja wejścia przed lookupem (wzór `REJECTION_REASON_REQUIRED`) — o ile nie łamie enumeration-safety po stronie użytkownika.
- [ ] Nowy handler w `GlobalExceptionHandler` buduj przez prywatny `errorBuilder(ErrorCode)` (pinuje `Content-Type: application/json`; bez tego `Accept: application/xml` kończy się 401 z `/error`, GH-140). Nie dodawaj `/error` do `permitAll`.
- [ ] Wyścig na UNIQUE/FK obsługuje handler `DataIntegrityViolationException` → 409 `DATA_CONFLICT`; guard w serwisie daje czytelny błąd, constraint jest drugą linią obrony.

## Testy kontrolera
- [ ] Przypadki: sukces, 400 walidacji, 401 anonim, 403 zła rola, 404 (w tym cudzy zasób), 409 dla każdego guarda stanu.
- [ ] Asercja na `$.errorCode`, nie tylko na statusie.
