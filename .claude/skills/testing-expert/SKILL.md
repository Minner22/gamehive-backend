---
name: testing-expert
description: Ekspert infrastruktury i wzorców testów GameHive — profil test (H2 PostgreSQL-mode bez Flyway, embedded Redis 16379, SyncTaskExecutor, wyłączone wyszukiwanie), tokeny w testach przez JwtService, MockMvc, testy AFTER_COMMIT bez @Transactional z własnymi danymi i sprzątaniem, ListAppender do logów, flush/clear przy kaskadach, czego suita nie dowodzi. Użyj przy pisaniu lub review testów, gdy test jest niestabilny albo zależy od kolejności, gdy coś przechodzi lokalnie, a pada w CI, przy pytaniach "jak przetestować X" oraz przy planowaniu sekcji "Testy" w planie issue.
---

# Ekspert: testy

Powiązane: `fixture-guard` (niezmienniki `data.sql`), `verify-dev` (to, czego testy nie dowodzą).

## Środowisko profilu `test` (`src/test/resources/application-test.yml`)
- **H2** `jdbc:h2:mem:testdb;MODE=PostgreSQL`, `ddl-auto: create-drop`, **Flyway wyłączony**. Schemat generuje Hibernate z encji, a `data.sql` ładuje się po nim (`defer-datasource-initialization`). `src/test/resources/schema.sql` **jest martwy** (do usunięcia w #150) — `schema-locations: []` wyłącza jego uruchamianie, a nic go nie referuje. Nie edytuj go w przekonaniu, że coś zmienisz.
- **Embedded Redis** na porcie 16379 (`config/EmbeddedRedisConfig`; startuje tylko wtedy, gdy port jest wolny, więc współdzielą go równoległe konteksty).
- `TestAsyncConfig`: oba executory to `SyncTaskExecutor`, więc `@Async` wykonuje się inline i `verify(...)` zaraz po wywołaniu jest deterministyczne.
- `gamehive.search.enabled: false` uruchamia fallbacki (`NoOpGameSearchService`, `DatabaseTaxonomySuggestService`). Wyjątek: `MeiliWiringTest`, który włącza wyszukiwanie i pisze ustawienia do lokalnego Meili, jeśli ten działa.
- JWT: ACCESS i ACTIVATION są ważne **2 s**, dlatego tokeny generuje się w `@BeforeEach`, a nie w `@BeforeAll` — token z początku klasy nie przeżyje dłuższego testu. Jedyne `Thread.sleep(1100)` (`AuthControllerTest`) służą przekroczeniu granicy pełnej sekundy przy epoce aktywacji (`iat` jest ucinany do sekund, porównanie jest ostre) — w przyszłych testach epok rób tak samo. Limit resubmisji = **2**.

## Wzorzec testu kontrolera
```java
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class XControllerTest {
    @Autowired MockMvc mockMvc; @Autowired JwtService jwtService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @MockitoBean JavaMailSender mailSender;
    @BeforeEach void setUp() {
        token = jwtService.generateToken("jane.smith@example.com", JwtTokenType.ACCESS, Set.of("ROLE_USER"));
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
}
```
- Tokeny generuj wprost przez `JwtService` dla seedowanych e-maili (john = ADMIN, jane = USER, mark.moderator = MODERATOR). Uprawnienia i tak pochodzą z bazy, nie z claimu.
- Asertuj `$.errorCode`, nie tylko status. Anonim = `isUnauthorized()`.
- Klasy z inną kombinacją `@MockitoBean` dostają **osobny kontekst Springa** (wolniej, więcej instancji). Przy nowej klasie trzymaj się zestawu mocków sąsiednich klas.

## Testy AFTER_COMMIT (audyt, indeks, sprzątanie, unieważnianie sesji)
- Klasa **bez `@Transactional`** — inaczej commit nie nastąpi i listener nie odpali.
- Dane **własne**, nie seedowane. Śledź utworzone id i sprzątaj w `@AfterEach`: dodatki przed grami, `existsById` przed delete (test mógł już skasować wiersz), `TransactionTemplate` wokół `delete(entity)` dla encji odłączonych, `auditRepository.deleteAll()`.
- Wzorce: `GameModerationServiceAuditTest`, `GameSubmissionAuditTest`, `GameSearchIndexingTest`, `TaxonomyIndexingTest`, `CollectionUserCleanupTest`.
- Klucze Redis z długim TTL (np. `search_reindex_lock`, 15 min) usuwaj w `@AfterEach`, bo przeciekną do klas, które nie robią `flushAll`.

## Zachowanie konektora Tomcata: prawdziwy port, nie MockMvc
Kompresja, `Content-Length`/`Transfer-Encoding` i inne nagłówki transportu powstają w konektorze, który MockMvc omija. Wzorzec z GH-143 (`config/HttpCompressionTest`, pierwszy test `RANDOM_PORT` w projekcie): `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@LocalServerPort` (pakiet `org.springframework.boot.test.web.server`) + zwykły `java.net.http.HttpClient`, który **nie rozpakowuje gzipa sam**, więc widać prawdziwe nagłówki i bajty. Dwie zasady: (1) serwer działa w innym wątku, więc `@Transactional` na teście **nie wycofa** zmian we wspólnym H2 — taki test tylko czyta albo sprząta po sobie w `@AfterEach`; (2) dobieraj odpowiedź świadomie: ciało napisane przez filtr (entry point Security) ma znaną długość, a odpowiedź kontrolera idzie chunked — zachowują się inaczej (pierwsza wersja testu kompresji przeszła na złym przykładzie i dopiero `verify-dev` to ujawnił). Sprawdzaj test mutacją konfiguracji: `mvn -Dtest=… "-DargLine=-Dwlasciwosc=wartosc" test` musi go zaczerwienić.

## Filtr servletowy: test jednostkowy z łańcuchem-sondą
Wzorzec z GH-144 (`common/logging/CorrelationIdFilterTest`): `new CorrelationIdFilter()` plus `MockHttpServletRequest`/`MockHttpServletResponse`, a jako `FilterChain` lambda, która zapisuje stan **z chwili wywołania łańcucha** (`(_, _) -> ref.set(MDC.get(...))`). Dopiero to pokazuje, co widzi reszta żądania. Po `doFilter` sprawdź sprzątanie (`MDC.get(...)` == null), także dla łańcucha rzucającego wyjątek. Czyść MDC w `@AfterEach`, bo test działa na wątku JUnit. Filtr trzeba jeszcze sprawdzić end-to-end przez MockMvc, który z `@AutoConfigureMockMvc` rejestruje beany `Filter`. Przy skutkach AFTER_COMMIT (audyt) taki test musi działać bez `@Transactional` (`CorrelationIdAuditRegressionTest`). Gdy test woła `force-logout`, po nim trzeba wyczyścić Redis i cache `userAuthState`, bo zostaje epoka sesji Jane.

## Testowanie kształtu zapytania, nie tylko wyniku
Gdy wymaganiem jest **jak** baza jest odpytywana (projekcja bez JOIN-a, jedno zapytanie zamiast N+1), wynik tego nie pokaże — te same id wyjdą też z JOIN-a. Wzorzec z GH-142: `support/CapturingStatementInspector` podpięty przez `@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=pl.m22.gamehive.support.CapturingStatementInspector")` w **osobnej klasie** (zmienia `EntityManagerFactory`, a więc kontekst), `em.clear()` + `CapturingStatementInspector.clear()` w `@BeforeEach`, potem asercje na liczbie instrukcji i treści SQL (`doesNotContain(" join ")`). Przykład: `collection/repository/CollectionIdProjectionSqlTest`. Statystyki Hibernate (`entityLoadCount`) są tu za słabe: zapytanie skalarne z JOIN-em też nie ładuje żadnej encji.

## Pułapki, które już kosztowały
- **Auto-flush Hibernate obejmuje tylko przestrzeń zapytań danego statementu**: `SELECT` na tabeli B nie wypchnie oczekującego `DELETE` na tabeli A. Przy teście kaskady w jednej transakcji zrób jawne `em.flush(); em.clear();` (`CollectionControllerTest`).
- Asercja `isEmpty()` nie wykryje brakującego escapowania `\` — testuj **trafienie** w wiersz z backslashem, a nie brak wyników (`DatabaseTaxonomySuggestServiceTest`).
- Mock `TaskInfo` buduj **przed** `when(...)`, inaczej Mockito zgłosi `UnfinishedStubbing`.
- **Awaria Redisa end-to-end (GH-168):** `@MockitoSpyBean RedisTemplate<String, String>` + `doThrow(QueryTimeoutException).when(redisTemplate).hasKey(...)` i `doReturn(mock(ValueOperations.class, _ -> { throw … }))` dla `opsForValue()` — przez PRAWDZIWE usługi przechodzą wtedy wszystkie tryby (strict, fail-open, połykanie). `Mockito.reset(redisTemplate)` = „Redis wraca” (sprawdzenie skutków, sprzątanie). Pokryj osobno awarię każdego odczytu, z którego korzysta ścieżka — przy pełnej awarii pierwszy strict odczyt maskuje regresję drugiego (`PasswordResetRedisUnavailableTest`).
- **Nagłówki w odpowiedzi błędu** (GH-148): MockMvc przechodzi przez prawdziwy `DispatcherServlet`, więc test „503 nadal ma `Set-Cookie`” dowodzi, że nagłówek ustawiony na `HttpServletResponse` przed wyjątkiem przetrwał `resetBuffer()`. Asertuj **dokładnie jeden** nagłówek (`getHeaders(SET_COOKIE)`), żeby złapać też duplikat na ścieżce 200 (`LogoutRedisUnavailableTest`). Przy częściowej awarii (np. tylko `opsForZSet()`) sprawdź po `Mockito.reset(redisTemplate)`, że drugi krok i tak się wykonał.
- **Atomowość skryptu Lua (GH-146)** testuj na embedded Redis dwoma wątkami startującymi z jednego `CountDownLatch`, w pętli (~30 iteracji, żeby wyścig realnie wystąpił), z asercją na multizbiór wyników (`containsExactlyInAnyOrder(CONSUMED, REUSED)`) — `RedisRefreshTokenStoreTest.rotate_concurrent_exactlyOneWinner`. Błąd w treści skryptu (np. literówka w nazwie komendy) wychodzi dopiero przy wykonaniu jako `RedisSystemException`, więc każda gałąź skryptu potrzebuje własnego testu. Mock dowolnego skryptu: `redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))` — `any(Object[].class)` łapie całe varargs niezależnie od liczby argumentów (Mockito 5).
- **Wyścig między żądaniami HTTP wymuszaj deterministycznie, nie losowo** (security review GH-146, `RefreshTokenReuseRaceTest`): `@MockitoSpyBean` na beanie w środku ścieżki, `doAnswer` zatrzymuje na `CountDownLatch` tylko wątek o nazwie `attacker-*` (własny `ThreadFactory`), drugie żądanie idzie w całości z wątku testu, potem zwolnienie. Punkt zatrzymania stawiaj **w oknie, które ma być zamknięte** (tu: zaraz po `rotate`) — po poprawce zatrzymanie przed rotacją przestało trafiać w okno i samo nie wyłapałoby regresji. Asercja na niezmienniku („żaden token z wyścigu nie działa”), nie na kolejności, którą test wymusił.
- **Limit refresh tokenów: nie zakładaj, że wypada pierwszy wydany.** Score w `user_refresh_tokens:{email}` to czas w ms; tokeny wydane w pętli trafiają w tę samą milisekundę, a przy równym score Redis porządkuje po wartości (JTI = losowy UUID), więc wypchnięty jest dowolny z nich. Ustal go z Redisa (`!hasKey("refresh_token:" + jti)`) — `RefreshTokenRotationTest.evictedByLimit_plainUnauthorized` przechodził przez to losowo, zanim to poprawiono.
- **Zdarzenie, które wylogowuje użytkownika** (np. `RefreshTokenReuseDetectedEvent`), w teście na współdzielonym kontekście publikuj dla konta **spoza `data.sql`** — revoke ustawia epokę sesji i kasuje refreshe, co inaczej przecieka do innych klas (`JwtServiceImplTest`, `@RecordApplicationEvents`). Logowanie przez prawdziwy `/login` w testach rób Johnem — hasło Jane w `data.sql` nie jest `password123`.
- **Wyścig z cache testuj prawdziwym `ConcurrentMapCache`** zamiast mocka: w `doAnswer` kroku pośredniego włóż do cache stan „żądania z okna” i sprawdź, że po operacji go nie ma (`UserSecurityEventListenerTest.staleAuthState*`). Mock z `verify(cache).evict` nie wykaże, że czyszczenie było w złym momencie.
- **Testy awarii Redis parametryzuj obiema postaciami niedostępności** — `@MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")` (odmowa połączenia + `QueryTimeoutException`). Testy mockujące tylko `RedisConnectionFailureException` przepuściły #165. Dodaj przypadek negatywny (`RedisSystemException`), jeśli kod ma być fail-open — pinuje, że nie łapie całego `DataAccessException`. Wzorce: `*FailureTest` w `auth/jwt/service`, `SearchReindexServiceTest`.
- Logi asertuje się przez Logback `ListAppender` na **właściwym** loggerze (wyniki zadań Meili loguje `MeiliIndexGateway`, a nie serwis) i odpina w `@AfterEach`.
- Wybór handlera wyjątku przez Springa da się udowodnić tylko end-to-end (MockMvc), nie bezpośrednim wywołaniem metody handlera.
- MockMvc nie ma kontenera, więc nie wykona ERROR dispatch do `/error` (GH-140 da się potwierdzić tylko na `dev`). Kolejność filtrów MockMvc z `@AutoConfigureMockMvc` za to respektuje (GH-163, `CorrelationIdSecurityOrderTest`).
- **ERROR dispatch w teście jednostkowym `OncePerRequestFilter`-a**: samo `setDispatcherType(ERROR)` nie wystarcza — `shouldNotFilterErrorDispatch` rozpoznaje ERROR dispatch po atrybucie `jakarta.servlet.error.request_uri` (`WebUtils.ERROR_REQUEST_URI_ATTRIBUTE`), który ustawia kontener. Bez niego mutacja `shouldNotFilterErrorDispatch() = true` przeżyła test (GH-163); wzorzec: `simulateErrorDispatch` w `CorrelationIdFilterTest`.
- Na `dev` ERROR dispatch wywołasz URL-em odrzucanym przez firewall Security (`/api/v1/games;x=1`, `%252e`): `sendError(400)` → `/error` → 401. Wyjątek z `JwtAuthFilter` (np. timeout Redis) go **nie** wywoła — filtr łapie `Exception`.
- Sort `id DESC` + domyślny rozmiar strony może wypchnąć stare fixture'y poza pierwszą stronę — przy asercjach na fixture'ach podawaj `size=50`.
- Adnotacji, której działanie zależy od proxy (`@Async`), nie da się przetestować behawioralnie przy executorze inline — pinuje ją asercja na adnotacji + `AopUtils.isAopProxy` (`SearchIndexExecutorTest`, `SearchFallbackTest`).
- Zielono lokalnie, czerwono w CI: CI buduje **merge commit z `master`** — najpierw zrób rebase.

## Testy na prawdziwym PostgreSQL (GH-154)
Wzorzec: `migration/FlywayPostgresMigrationTest` — `@Tag("postgres")` + `@Testcontainers` + `@Container @ServiceConnection static PostgreSQLContainer` (Testcontainers 2.x: klasa w pakiecie `org.testcontainers.postgresql`, artefakty `testcontainers-*`) + `@ActiveProfiles({"test", "it-postgres"})`. Profil `it-postgres` **nakłada się** na `test` (późniejszy wygrywa): Flyway on, `ddl-auto=validate`, `defer-datasource-initialization: false` (z Flyway `true` daje cykl), `sql.init.mode: never`, jawny sterownik PostgreSQL (profil `test` ustawia H2). Tag `postgres` jest wykluczony z domyślnego `./mvnw test` (`<excludedGroups>` w `pom.xml`); `-Pintegration` odwraca filtr i uruchamia **tylko** te testy (job `integration`). Bez `disabledWithoutDocker` — w CI brak Dockera ma czerwienić. Asercje liczące migracje biorą liczbę z classpath, nie literał. Mutacja „pole w encji bez migracji” jest właściwym sprawdzianem (H2 zielone, PostgreSQL czerwone); zmiana nazwy kolumny czerwieni też H2, bo `data.sql` używa starych nazw.

## Czego suita nie dowodzi (→ `verify-dev`)
Lokalny `./mvnw test`: migracji Flyway i wszystkiego, co jest tylko w SQL (DEFAULT-y, indeksy, `ON DELETE` bez `@OnDelete`) — **to pokrywa job `integration` w CI (GH-154)**, poza DEFAULT-ami, `nullable` i `length`. Dalej poza zasięgiem: Meilisearch, prawdziwej puli `@Async` i propagacji MDC, ERROR dispatch, zachowania specyficznego dla PostgreSQL (tryb zgodności H2 nie jest pełny: collation, `LIKE`, typy, plany zapytań).

## Uruchamianie
`./mvnw -Dtest=Klasa test`, `./mvnw -Dtest=Klasa#metoda test` (Maven Wrapper, GH-153; z PowerShella `mvnw.cmd` i argumenty `-D…` w cudzysłowach, bo PowerShell tnie je na `.` i `,`). Po każdej zmianie `data.sql` albo współdzielonej infrastruktury puść **pełny** `./mvnw test` — zależności są międzyklasowe. **Czerwony run CI**: raporty są w artefakcie `surefire-reports` joba `test` (`gh run download <run-id> -n surefire-reports`), nie trzeba przewijać logu.
