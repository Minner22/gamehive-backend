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
| Błędy | `exception/BaseException` + `Domain`/`Application`/`InfrastructureException`, `ErrorCode`, `GlobalExceptionHandler`, `ApiError`, `ApiValidationError`, `FieldValidationError` | podklasa wyznacza poziom logu, `ErrorCode` status HTTP; odpowiedzi przez `errorBuilder` (pinuje JSON); **`ApiError.message` = zawsze `ErrorCode.defaultMessage`** (GH-149) — komunikat wyjątku to szczegół do logu, handler loguje go na poziomie podklasy; pinują `GlobalExceptionHandlerTest` + `ApiErrorStaticMessageTest` |
| Logowanie | `logging/CorrelationIdFilter` (`OncePerRequestFilter` **przed** łańcuchem Security, `@Order(HIGHEST_PRECEDENCE + 1)`, także na ERROR dispatchu; nagłówek `X-Correlation-Id`, zwalidowany albo zastąpiony UUID → atrybut requestu → MDC `correlationId` → nagłówek odpowiedzi; GH-144, GH-163), `LoggingAspect` (`within(@RestController *)` → `Handled request: controller= method= duration=`; działa tylko z `spring-boot-starter-aspectj` — w Boot 4 następca `starter-aop`, wersja z parenta od GH-155; bez autokonfiguracji AOP bean `@Aspect` powstaje, ale nic go nie stosuje — pinuje `LoggingAspectTest`), `MdcTaskDecorator` (kopiuje MDC do wątków `@Async` w chwili zlecenia), `LoggingUtils.obfuscateEmail` (null-safe → `<empty>` od GH-150, bo bywa wołany w `catch`; pinuje `LoggingUtilsTest`) |
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

## Nagłówek `X-Correlation-Id` (GH-144)
`CorrelationIdFilter.resolveCorrelationId` przyjmuje nagłówek tylko wtedy, gdy pasuje do `[A-Za-z0-9._-]{1,64}` (`matches()`, czyli cały string). Brak nagłówka, pusty nagłówek, za długi albo z innymi znakami → nowy UUID, ten sam w MDC, w nagłówku odpowiedzi i w audycie. Odrzucenie loguje się na DEBUG **tylko z długością** — treść to ładunek, przed którym się bronimy. To nie jest błąd klienta (nie 400), bo nagłówek jest opcjonalny.
- Dlaczego: przed GH-144 wartość dłuższa niż 64 znaki dawała 2xx, ale insert do `correlation_id VARCHAR(64)` w `REQUIRES_NEW` po commicie padał, a Spring tylko go logował (`TransactionSynchronizationUtils`). Wpis audytu znikał, więc admin albo moderator mógł jednym nagłówkiem wymazać ślad własnej akcji. Do tego znaki sterujące trafiały do każdej linii logu żądania.
- `MAX_CORRELATION_ID_LENGTH` (64) jest podpięty pod `@Column(length = …)` w `UserAuditLogEntry` i `ContentModerationAuditLog`. **Podniesienie limitu wymaga nowej migracji** dla V4/V10: H2 dostanie nową długość sam, PostgreSQL nie.
- Filtr jest jedynym źródłem `correlationId` w MDC (`MdcTaskDecorator` tylko kopiuje), dlatego zdarzenia audytu nie przycinają wartości.
- Testy: `CorrelationIdFilterTest` (granice 64/65, znaki sterujące, czyszczenie MDC) i `CorrelationIdAuditRegressionTest` (`force-logout` i `approve` z nagłówkiem 500 znaków dają dokładnie 1 wpis; po cofnięciu walidacji 0 wpisów).

## Kolejność i ERROR dispatch (GH-163)
- **Przed Security:** `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` (Security ma `-100`; `+1` zostawia pierwsze miejsce Boot-owemu `CharacterEncodingFilter`). Bez tego filtr dostawał `LOWEST_PRECEDENCE`, więc 401/403 kończone przez łańcuch nie miały nagłówka, a logi `JwtAuthFilter` nie miały MDC.
- **Typy dispatchu zależą od klasy filtra** (Boot 4, `AbstractFilterRegistrationBean.determineDispatcherTypes`): zwykły `Filter` dostaje tylko `REQUEST`, `OncePerRequestFilter` — **wszystkie**. Dlatego filtr dziedziczy po `OncePerRequestFilter` i ma `shouldNotFilterErrorDispatch() = false`.
- **Atrybut requestu `CORRELATION_ID_ATTRIBUTE`:** Tomcat robi ERROR dispatch do `/error` dopiero po wyjściu z pierwotnego łańcucha — MDC jest już wyczyszczone, a znacznik „already filtered” zdjęty, więc filtr wykonuje się od nowa przez `doFilterInternal`. Bez atrybutu przy braku nagłówka klienta wygenerowałby drugi UUID. Atrybut ma pierwszeństwo przed nagłówkiem (był już raz zwalidowany).
- Zweryfikowane na `dev` 2026-11-22: URL z `;` albo `%25` → firewall Security → `sendError(400)` → ERROR dispatch → 401; `Rejecting request…` i `Securing GET /error` mają ten sam `correlationId` co nagłówek odpowiedzi (widać tylko z `--logging.level.org.springframework.security.web.FilterChainProxy=DEBUG`).
- Testy: `CorrelationIdFilterTest` (ERROR dispatch przez `simulateErrorDispatch`), `CorrelationIdSecurityOrderTest` (kolejność w MockMvc).

## Testy
`common/domain/*Test`, `common/exception/{GlobalExceptionHandlerTest, RoutingErrorMappingTest}`, `common/logging/{MdcTaskDecoratorTest, CorrelationIdFilterTest, CorrelationIdAuditRegressionTest, CorrelationIdSecurityOrderTest}`, `common/persistence/EntityEqualsHashCodeTest`, `common/email/service/MailServiceImplTest`, `config/{SearchIndexExecutorTest, OpenApiDocumentationTest}`.

## Obserwacje z analizy kodu (2026-11-21) — zgłoszone jako issues, nie naprawiaj samodzielnie
1. ~~[#144] `CorrelationIdFilter` ufa nagłówkowi klienta bez ograniczeń~~ — **rozwiązane w GH-144** (opis w sekcji „Nagłówek `X-Correlation-Id`” wyżej).
2. ~~[#163] `CorrelationIdFilter` działa po łańcuchu Spring Security~~ — **rozwiązane w GH-163** (sekcja „Kolejność i ERROR dispatch” niżej).
3. ~~[#149] Komunikaty `BaseException` trafiają do klienta 1:1~~ — **rozwiązane w GH-149** (odpowiedź = `defaultMessage`, szczegół tylko w logu; reguła przekrojowa w CLAUDE.md, "Error handling"). Było: — każdy `throw new …Exception(CODE, "… " + dane)` to dane w odpowiedzi HTTP. Reguła "statyczne komunikaty" jest dziś udokumentowana tylko dla wyszukiwarki; moduły `auth`/`user` interpolują (zamaskowane) e-maile i nazwy użytkowników.
