---
name: auth-expert
description: Ekspert modułu auth GameHive (pl.m22.gamehive.auth) — rejestracja, aktywacja, logowanie enumeration-safe, cztery typy JWT (ACCESS/REFRESH/ACTIVATION/PASSWORD_RESET), JwtAuthFilter, SecurityConfig, Redis (refresh store z limitem, blacklist JTI, epoki sesji i aktywacji), cache stanu użytkownika, maile auth na @Async. Użyj przy każdym pytaniu "jak działa logowanie/token/wylogowanie/reset hasła", przy planowaniu, review lub debugowaniu zmian w auth/, SecurityConfig, JwtAuthFilter, w kluczach Redis, przy 401 o niejasnej przyczynie oraz przed /security-review.
---

# Ekspert: `auth/`

Jak pracować: każdą odpowiedź opieraj na kodzie (podawaj `plik:linia`), a nie na tym skillu — skill mówi, **gdzie** patrzeć i **czego nie zepsuć**. Reguły przekrojowe (GH-63, audyt, cache) są też w CLAUDE.md, w sekcji "Security & JWT pipeline".

## Mapa kodu
| Klasa | Odpowiedzialność |
|---|---|
| `controller/AuthController` | jedyne wejście `/api/v1/auth/**`. **Część logiki tokenów żyje tutaj, nie w serwisie**: `/activate` i `/password-reset/confirm` walidują token wspólnym prywatnym `validateOneTimeToken` (podpis/typ/exp → blacklista JTI → epoka `iat < epoka` → `TOKEN_REVOKED`; komunikaty to stała etykieta + stały tekst, GH-145), a po sukcesie same blacklistują token; `generateTokens` buduje cookie refresh |
| `service/AuthServiceImpl` | `register` (+`UserRegisteredEvent`), `login` (stały czas: dummy bcrypt), `activateUser`, `requestPasswordReset` (milczy przy nieznanym e-mailu), `confirmPasswordReset` (+`UserCredentialsChangedEvent` + audyt `PASSWORD_CHANGE`), `resendActivationEmail` (tylko nieaktywni) |
| `jwt/service/JwtServiceImpl` | Nimbus JOSE, HS256, osobny sekret per typ; `validateToken` = algorytm → podpis → `exp` → claim `type` → (REFRESH) JTI musi istnieć w Redis. Generowanie REFRESH zapisuje JTI w Redis |
| `jwt/service/RedisRefreshTokenStore` | `refresh_token:{jti}` → email (TTL = ważność), ZSET `user_tokens:{email}` (score = czas wydania); limit `maxActiveTokensPerUser` (5) usuwa najstarszy |
| `jwt/service/TokenBlacklistServiceImpl` | `blacklist:{jti}` z TTL = pozostała ważność; **fail-open** przy odczycie, zapis przy awarii Redis jest połykany (log ERROR) |
| `jwt/service/RedisSessionEpochStore` | `token_invalid_after:{email}` (ms, TTL = ważność ACCESS) oraz `activation_invalid_after:{email}` i `password_reset_invalid_after:{email}` (GH-145) — obie **ucięte do pełnych sekund**, TTL = ważność danego typu tokena; odczyt **fail-open** (null), zapis rzuca `REDIS_UNAVAILABLE` |
| `jwt/service/UserAuthStateProvider` | `@Cacheable("userAuthState")`: `{enabled, authorities z DB, invalidAfter}` |
| `config/JwtAuthFilter` | Bearer → validate ACCESS → blacklist (fallthrough do entry pointu) → stan użytkownika: disabled = `ACCOUNT_DISABLED`, `iat < epoka` = `TOKEN_REVOKED` (oba pisze sam jako `ApiError`) → principal = **email jako String**. `shouldNotFilter`: login/register/refresh |
| `config/SecurityConfig` | stateless, CSRF off, `permitAll`: h2-console, `/api/v1/auth/**`, swagger; entry point = 401 `ACCESS_DENIED`; `DelegatingPasswordEncoder` (`{bcrypt}`); `FilterRegistrationBean<JwtAuthFilter>` z `setEnabled(false)` — `JwtAuthFilter` działa **tylko** w łańcuchu Security (GH-163) |
| `event/AuthEmailEventListener` | `@Async("authEmailExecutor")` + AFTER_COMMIT; generuje token i wysyła mail; przy resend aktywacji i przy **każdym** żądaniu resetu hasła **najpierw** epoka (fail-closed: Redis padł = brak maila, endpoint i tak 200) |
| `jwt/config/*Properties` | `jwt.{access,refresh,activation,passwordreset}.{secret,validityInSeconds}`; w `test` ACCESS i ACTIVATION żyją **2 s** |

## Przepływy, które trzeba znać
- **Uprawnienia pochodzą z bazy, nie z tokena.** Claim `roles` w ACCESS jest wymagany przy generowaniu, ale filtr bierze `authorities` z `UserAuthStateProvider`. Zmiana ról działa więc od razu po eviction cache — bez nowego tokena.
- **Unieważnianie (GH-63)**: zdarzenia z `user/event` → `UserSecurityEventListener` (AFTER_COMMIT) → `revoke` = usunięcie refreshów + epoka sesji + eviction cache. Reaktywacja i zmiana ról robią tylko eviction (bez czyszczenia epoki).
- **Logout**: blacklist ACCESS + `revokeUsersTokens` (wszystkie refreshe użytkownika, czyli wylogowanie ze wszystkich urządzeń) + wyczyszczenie cookie.
- **Refresh**: cookie `refreshToken` (path `/api/v1/auth/refresh`, HttpOnly, Secure, SameSite=Strict, 7 dni) → nowa para tokenów. **Bez rotacji**: stary refresh zostaje ważny do TTL albo do wypchnięcia przez limit 5.
- `AppUserDetailsService` + bean `authenticationProvider` istnieją, ale `login` **nie** używa `AuthenticationManager` — weryfikuje hasło sam (`HashedPassword.matches`).

## Niezmienniki — nie psuć
- `login`: nieznany e-mail i złe hasło = ten sam `INVALID_CREDENTIALS`; hasło sprawdzane **zawsze i jako pierwsze**; `USER_NOT_ACTIVATED` dopiero po poprawnym haśle.
- Porównania epok są **ostre** (`<`); epoki aktywacji i resetu hasła ucięte do sekund (inaczej świeży token wystawiony zaraz po epoce byłby odrzucony — patrz pamięć `project_resend_activation_design`). Pinuje to `AuthControllerTest.passwordReset_linkIssuedRightAfterEpoch_stillValid` i test resendu aktywacji; zamiana `<` na `<=` czerwieni oba.
- Epoka resetu hasła jest ustawiana przy żądaniu **i** po udanym resecie (`UserSecurityEventListener.onUserCredentialsChanged`, przed `revoke`) — ta druga chroni przed linkami z tej samej sekundy co ostatnie żądanie. Deaktywacja, delete i force-logout jej **nie** ruszają (to nie zmiana hasła).
- Side effecty bezpieczeństwa są **AFTER_COMMIT**, więc metoda serwisu publikująca zdarzenie **musi** być `@Transactional` (inaczej zdarzenie nie dotrze).
- Nieuwierzytelniony = **401**, nigdy 403. Nowe publiczne endpointy muszą leżeć pod `/api/v1/auth/**`. Gdy mają działać bez nagłówka, a mogą dostać stary Bearer, rozważ dopisanie do `shouldNotFilter`.
- Fail-open (blacklist, epoka) vs fail-closed (resend aktywacji, zapis refreshu) to świadome decyzje — nie ujednolicaj ich bez rozmowy.
- **Filtr-`@Component` przeznaczony dla łańcucha Security wymaga `FilterRegistrationBean` z `setEnabled(false)`** (GH-163). Inaczej Boot zarejestruje go drugi raz jako filtr servletowy (`LOWEST_PRECEDENCE`, a dla `OncePerRequestFilter` wszystkie typy dispatchu) — poza łańcuchem, po autoryzacji. Pinuje `security/JwtAuthFilterRegistrationTest` (przez `ServletContextInitializerBeans`, tę samą kolekcję, z której rejestruje Boot); bez beana test widzi włączoną rejestrację.
- `CorrelationIdFilter` działa **przed** łańcuchem (GH-163), więc każda odpowiedź 401/403 z Security ma `X-Correlation-Id`, a logi filtra mają MDC (`CorrelationIdSecurityOrderTest`).

## Testy, które to pinują
`auth/controller/AuthControllerTest`, `auth/service/AuthServiceImplTest`, `AuthServiceRollbackTest` (rollback = brak maila), `auth/jwt/service/*Test` (`RedisSessionEpochStoreFailureTest` = fail-open), `auth/event/AuthEmailEventListenerTest`, a przekrojowo pakiet `security/`: `AccessTokenInvalidationTest`, `ForceLogoutTest`, `PasswordChangeInvalidatesSessionsTest`, `RefreshTokenRevocationTest`, `UserAuthStateCacheTest`, `SecurityIntegrationTest`. Testy generują tokeny wprost przez `jwtService.generateToken(...)` i robią `flushAll()` na embedded Redis w `@BeforeEach`.

## Obserwacje z analizy kodu (2026-11-21) — zgłoszone jako issues, nie naprawiaj samodzielnie
1. **[#149] Komunikaty błędów zawierają dane wejściowe** — `GlobalExceptionHandler.buildResponse` kopiuje `ex.getMessage()` do `ApiError`, więc klient dostaje np. `"Invalid credentials for: jo***@example.com"` albo `"Username already exists: <username>"`. To nie jest wyrocznia enumeracji (obie gałęzie loginu mają ten sam kształt, a echo dotyczy własnego wejścia wołającego), ale stoi w sprzeczności z regułą "static messages", którą przyjęto dla wyszukiwarki (GH-122).
2. ~~[#145] Blacklistowany token resetu hasła dostaje komunikat o tokenie aktywacyjnym~~ — **rozwiązane w GH-145** (wspólny helper z etykietą typu tokena).
3. ~~[#145] Starsze linki resetu hasła nie są unieważniane~~ — **rozwiązane w GH-145** (epoka `password_reset_invalid_after`, zob. mapa i niezmienniki; zweryfikowane na `dev` 2026-11-21).
4. **[#146] Brak rotacji refresh tokenów i wykrywania ich ponownego użycia** (patrz "Refresh" wyżej). Skradziony refresh żyje do 7 dni albo do wylogowania.
5. **[#148] Logout przy awarii Redis**: zapis do blacklisty jest połykany, a potem `revokeAllByUserEmail` rzuca `REDIS_UNAVAILABLE` — klient dostaje 503, cookie nie jest czyszczone, a access token żyje dalej (≤15 min).
6. [#150] `RedisRefreshTokenStore` loguje **pełny e-mail** na poziomie ERROR; reszta modułu używa `obfuscated()`.
7. **[#165] Timeout Redis omija obsługę awarii — potwierdzone na `dev` 2026-11-21.** `RedisSessionEpochStore`, `TokenBlacklistServiceImpl`, `RedisRefreshTokenStore` (i `SearchReindexService`) łapią tylko `RedisConnectionFailureException`, a zawieszony Redis (`docker pause`) daje po `spring.data.redis.timeout` (2 s) **`QueryTimeoutException`**. Fail-open odczytów (blacklista, epoki) i `REDIS_UNAVAILABLE` zapisów działają więc tylko przy odmowie połączenia; przy timeoucie leci nieprzetłumaczony wyjątek — w kontrolerze (`/activate`, `/password-reset/confirm`) 500, a w `JwtAuthFilter` połyka go `catch (Exception e)` z logiem tylko na DEBUG, więc żądanie z **poprawnym** tokenem dostaje 401 `ACCESS_DENIED` (de facto fail-closed i niewidoczne; potwierdzone na `dev` 2026-11-22, korekta w komentarzu #165). Testy awarii (`*FailureTest`) mockują tylko `RedisConnectionFailureException`, dlatego tego nie widać.
8. Kosmetyka: `RedisSessionEpochStore.getEpoch` loguje przy błędzie odczytu „…while **setting** epoch…” (skopiowane z `setEpoch`).
