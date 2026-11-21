---
name: common-expert
description: Ekspert pakietów współdzielonych GameHive — common/ (hierarchia encji AbstractEntity/LongEntity/UuidEntity/ModeratedLongEntity, Specifications i RepositoryLookups, value objects Email/Username/HashedPassword/PhoneNumber/ProfilePictureUrl, wyjątki i GlobalExceptionHandler, ErrorCode, logowanie z CorrelationIdFilter/LoggingAspect/MdcTaskDecorator, MailService) oraz config/ (AsyncConfig z dwoma executorami, CacheConfig/Caffeine, OpenApiConfig, DevDataInitializer, DevGameSeeder). Użyj przy zmianach i pytaniach w tych pakietach, przy nowym executorze @Async, nowym cache, nowym value object, nowej encji bazowej, przy debugowaniu logów i correlationId, oraz zanim moduł zacznie zależeć od czegoś w common.
---

# Ekspert: `common/` + `config/`

Zasada nadrzędna: **`common` nie zależy od żadnego modułu domenowego** (dlatego `ModeratedLongEntity` i `ModerationStatus` leżą tutaj, a nie w `game/model`). Przed dodaniem czegoś do `common` upewnij się, że to naprawdę jest współdzielone (≥2 moduły) — precedensy wydzielenia: `Specifications.joinEqualsIfPresent` (GH-120), `RepositoryLookups` (GH-120).

## Mapa kodu
| Obszar | Klasy | Uwagi |
|---|---|---|
| Encje bazowe | `persistence/AbstractEntity<ID>`, `LongEntity`, `UuidEntity`, `ModeratedLongEntity`, `ModerationStatus` | timestampy z `@PrePersist`/`@PreUpdate`; equals/hashCode po id (pinuje `EntityEqualsHashCodeTest`); UUID v7 tylko dla `AppUser` |
| Zapytania | `persistence/Specifications` | idiom "pomiń, gdy null": `equalsIfPresent`, `lessThanOrEqualToIfPresent`, `greaterThanOrEqualToIfPresent`, `joinEqualsIfPresent` (+ `distinct`), `likeIgnoreCaseIfPresent` + `likePattern` (escape `%`, `_`, `\` ze znakiem `LIKE_ESCAPE`) |
| | `persistence/RepositoryLookups` | `findAllOrThrow(repo, ids, ErrorCode)` (jeden `findAllById` + porównanie liczności), `nullSafe` |
| Value objects | `domain/Email` (Bean Validation + `obfuscated()`), `Username` (`^[a-zA-Z0-9._-]{3,30}$`), `HashedPassword` (`{id}hash`, `fromRaw`/`fromHash`/`matches`), `PhoneNumber` (E.164), `ProfilePictureUrl` | rekordy walidujące w konstruktorze kanonicznym → `DomainException` z `INVALID_*`; `toString()` zwraca surową wartość — **przy logowaniu e-maila używaj `obfuscated()`** |
| Błędy | `exception/BaseException` + `Domain`/`Application`/`InfrastructureException`, `ErrorCode`, `GlobalExceptionHandler`, `ApiError`, `ApiValidationError`, `FieldValidationError` | podklasa wyznacza poziom logu, `ErrorCode` status HTTP; odpowiedzi przez `errorBuilder` (pinuje JSON); **`ApiError.message` = `ex.getMessage()`**, a bez komunikatu w konstruktorze = `defaultMessage` |
| Logowanie | `logging/CorrelationIdFilter` (nagłówek `X-Correlation-Id` → MDC `correlationId` → nagłówek odpowiedzi), `LoggingAspect` (`within(@RestController *)` → `Handled request: controller= method= duration=`), `MdcTaskDecorator` (kopiuje MDC do wątków `@Async` w chwili zlecenia), `LoggingUtils.obfuscateEmail` |
| Mail | `email/service/MailServiceImpl`, `email/config/MailProperties` | linki prowadzą na front (`spring.mail.activationAddress`/`passwordResetAddress`); w testach `@MockitoBean JavaMailSender` |
| Async | `config/AsyncConfig` | `authEmailExecutor`: 2–5 wątków, kolejka 100, **`CallerRunsPolicy`** (backpressure, mail może poczekać na wątku żądania); `searchIndexExecutor`: **1 wątek** (porządek FIFO), kolejka 500, **odrzuć + log ERROR**. Oba z `MdcTaskDecorator`, `waitForTasksToCompleteOnShutdown` + 30 s; oba `@Profile("!test")` |
| Cache | `config/CacheConfig` | Caffeine, jeden cache `userAuthState` (60 s, 10 000 wpisów), lokalny dla instancji |
| HTTP | `server.compression` w `application.yml` (GH-143) | gzip w konektorze Tomcata; `min-response-size` działa tylko dla odpowiedzi o znanej długości (entry point Security, zasoby statyczne), a odpowiedzi MVC idą chunked i są kompresowane zawsze — świadoma decyzja, szczegóły i uzasadnienie (BREACH) w CLAUDE.md |
| Dev | `config/DevDataInitializer` → `DevGameSeeder` | tylko profil `dev`; szczegóły w skillu `verify-dev` |
| OpenAPI | `config/OpenApiConfig` | `@OpenAPIDefinition` + schemat `bearerAuth` |

## Przepisy na typowe zmiany
- **Nowy executor `@Async`**: bean w `AsyncConfig` z `@Profile("!test")` **oraz** bliźniaczy `SyncTaskExecutor` o tej samej nazwie w `src/test/.../config/TestAsyncConfig` — bez niego kontekst testowy nie wstanie. Polityka odrzucania to decyzja produktowa: czy wolno blokować wątek żądania? (mail: tak, indeks: nie). Metoda `@Async` musi być publiczna i wołana przez proxy.
- **Nowy cache**: nazwa jako stała w `CacheConfig`, a do tego jawna eviction przy każdej mutacji (TTL jest tylko siatką bezpieczeństwa). Pamiętaj, że Caffeine nie synchronizuje się między instancjami.
- **Nowy value object**: rekord, walidacja w konstruktorze kanonicznym, `ErrorCode` `INVALID_*` w sekcji Domain, `@Embedded` + `@AttributeOverride(name = "value", …)` na encji, test w `common/domain`.
- **Nowy handler wyjątku**: przez `errorBuilder`; błąd klienta = WARN bez stack trace'a, defekt własnego kodu = ERROR ze stack trace'em (wzór `MissingPathVariableException`).
- **Side effect po commicie**: `@TransactionalEventListener(AFTER_COMMIT)` → osobny bean `@Transactional(REQUIRES_NEW)`, `correlationId` zrzucony z MDC **w chwili publikacji** (po commicie, na innym wątku, MDC może już być puste).

## Testy
`common/domain/*Test`, `common/exception/{GlobalExceptionHandlerTest, RoutingErrorMappingTest}`, `common/logging/MdcTaskDecoratorTest`, `common/persistence/EntityEqualsHashCodeTest`, `common/email/service/MailServiceImplTest`, `config/{SearchIndexExecutorTest, OpenApiDocumentationTest}`.

## Obserwacje z analizy kodu (2026-11-21) — zgłoszone jako issues, nie naprawiaj samodzielnie
1. **[#144] `CorrelationIdFilter` ufa nagłówkowi klienta bez ograniczeń** — dowolna długość i dowolne znaki. Skutki: (a) wpis w logach z treścią kontrolowaną przez klienta (log injection przy wzorcu tekstowym); (b) **wartość dłuższa niż 64 znaki gubi wpisy audytu (potwierdzone 2026-11-21)**, bo `correlation_id` to `VARCHAR(64)` w V4 i V10, a błąd zapisu w `REQUIRES_NEW` jest tylko logowany przez `TransactionSynchronizationUtils` (szczegóły w `user-expert`). Typowa naprawa: przyjmować tylko `[A-Za-z0-9-]{1,64}`, a w pozostałych przypadkach generować UUID.
2. [#149] Komunikaty `BaseException` trafiają do klienta 1:1 — każdy `throw new …Exception(CODE, "… " + dane)` to dane w odpowiedzi HTTP. Reguła "statyczne komunikaty" jest dziś udokumentowana tylko dla wyszukiwarki; moduły `auth`/`user` interpolują (zamaskowane) e-maile i nazwy użytkowników.
