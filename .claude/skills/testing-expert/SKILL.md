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
- **Testy awarii Redis parametryzuj obiema postaciami niedostępności** — `@MethodSource("pl.m22.gamehive.support.RedisUnavailability#failures")` (odmowa połączenia + `QueryTimeoutException`). Testy mockujące tylko `RedisConnectionFailureException` przepuściły #165. Dodaj przypadek negatywny (`RedisSystemException`), jeśli kod ma być fail-open — pinuje, że nie łapie całego `DataAccessException`. Wzorce: `*FailureTest` w `auth/jwt/service`, `SearchReindexServiceTest`.
- Logi asertuje się przez Logback `ListAppender` na **właściwym** loggerze (wyniki zadań Meili loguje `MeiliIndexGateway`, a nie serwis) i odpina w `@AfterEach`.
- Wybór handlera wyjątku przez Springa da się udowodnić tylko end-to-end (MockMvc), nie bezpośrednim wywołaniem metody handlera.
- MockMvc nie ma kontenera, więc nie wykona ERROR dispatch do `/error` (GH-140 da się potwierdzić tylko na `dev`). Kolejność filtrów MockMvc z `@AutoConfigureMockMvc` za to respektuje (GH-163, `CorrelationIdSecurityOrderTest`).
- **ERROR dispatch w teście jednostkowym `OncePerRequestFilter`-a**: samo `setDispatcherType(ERROR)` nie wystarcza — `shouldNotFilterErrorDispatch` rozpoznaje ERROR dispatch po atrybucie `jakarta.servlet.error.request_uri` (`WebUtils.ERROR_REQUEST_URI_ATTRIBUTE`), który ustawia kontener. Bez niego mutacja `shouldNotFilterErrorDispatch() = true` przeżyła test (GH-163); wzorzec: `simulateErrorDispatch` w `CorrelationIdFilterTest`.
- Na `dev` ERROR dispatch wywołasz URL-em odrzucanym przez firewall Security (`/api/v1/games;x=1`, `%252e`): `sendError(400)` → `/error` → 401. Wyjątek z `JwtAuthFilter` (np. timeout Redis) go **nie** wywoła — filtr łapie `Exception`.
- Sort `id DESC` + domyślny rozmiar strony może wypchnąć stare fixture'y poza pierwszą stronę — przy asercjach na fixture'ach podawaj `size=50`.
- Adnotacji, której działanie zależy od proxy (`@Async`), nie da się przetestować behawioralnie przy executorze inline — pinuje ją asercja na adnotacji + `AopUtils.isAopProxy` (`SearchIndexExecutorTest`, `SearchFallbackTest`).
- Zielono lokalnie, czerwono w CI: CI buduje **merge commit z `master`** — najpierw zrób rebase.

## Czego suita nie dowodzi (→ `verify-dev`)
Migracji Flyway i wszystkiego, co jest tylko w SQL (DEFAULT-y, indeksy, `ON DELETE` bez `@OnDelete`), Meilisearch, prawdziwej puli `@Async` i propagacji MDC, ERROR dispatch, zachowania specyficznego dla PostgreSQL (tryb zgodności H2 nie jest pełny: collation, `LIKE`, typy, plany zapytań).

## Uruchamianie
`mvn -Dtest=Klasa test`, `mvn -Dtest=Klasa#metoda test`. Po każdej zmianie `data.sql` albo współdzielonej infrastruktury puść **pełny** `mvn test` — zależności są międzyklasowe.
