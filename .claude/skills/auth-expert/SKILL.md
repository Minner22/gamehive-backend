---
name: auth-expert
description: Ekspert modułu auth GameHive (pl.m22.gamehive.auth) — rejestracja, aktywacja, logowanie enumeration-safe, cztery typy JWT (ACCESS/REFRESH/ACTIVATION/PASSWORD_RESET), JwtAuthFilter, SecurityConfig, Redis (refresh store z limitem, blacklist JTI, epoki sesji i aktywacji), cache stanu użytkownika, maile auth na @Async. Użyj przy każdym pytaniu "jak działa logowanie/token/wylogowanie/reset hasła", przy planowaniu, review lub debugowaniu zmian w auth/, SecurityConfig, JwtAuthFilter, w kluczach Redis, przy 401 o niejasnej przyczynie oraz przed /security-review.
---

# Ekspert: `auth/`

Jak pracować: każdą odpowiedź opieraj na kodzie (podawaj `plik:linia`), a nie na tym skillu — skill mówi, **gdzie** patrzeć i **czego nie zepsuć**. Reguły przekrojowe (GH-63, audyt, cache) są też w CLAUDE.md, w sekcji "Security & JWT pipeline".

## Mapa kodu
| Klasa | Odpowiedzialność |
|---|---|
| `controller/AuthController` | jedyne wejście `/api/v1/auth/**`. **Część logiki tokenów żyje tutaj, nie w serwisie**: `/activate` i `/password-reset/confirm` same walidują token, sprawdzają blacklistę i epokę, a po sukcesie blacklistują token; `generateTokens` buduje cookie refresh |
| `service/AuthServiceImpl` | `register` (+`UserRegisteredEvent`), `login` (stały czas: dummy bcrypt), `activateUser`, `requestPasswordReset` (milczy przy nieznanym e-mailu), `confirmPasswordReset` (+`UserCredentialsChangedEvent` + audyt `PASSWORD_CHANGE`), `resendActivationEmail` (tylko nieaktywni) |
| `jwt/service/JwtServiceImpl` | Nimbus JOSE, HS256, osobny sekret per typ; `validateToken` = algorytm → podpis → `exp` → claim `type` → (REFRESH) JTI musi istnieć w Redis. Generowanie REFRESH zapisuje JTI w Redis |
| `jwt/service/RedisRefreshTokenStore` | `refresh_token:{jti}` → email (TTL = ważność), ZSET `user_tokens:{email}` (score = czas wydania); limit `maxActiveTokensPerUser` (5) usuwa najstarszy |
| `jwt/service/TokenBlacklistServiceImpl` | `blacklist:{jti}` z TTL = pozostała ważność; **fail-open** przy odczycie, zapis przy awarii Redis jest połykany (log ERROR) |
| `jwt/service/RedisSessionEpochStore` | `token_invalid_after:{email}` (ms, TTL = ważność ACCESS) oraz `activation_invalid_after:{email}` (**ucięte do pełnych sekund**, TTL = ważność ACTIVATION); odczyt **fail-open** (null) |
| `jwt/service/UserAuthStateProvider` | `@Cacheable("userAuthState")`: `{enabled, authorities z DB, invalidAfter}` |
| `config/JwtAuthFilter` | Bearer → validate ACCESS → blacklist (fallthrough do entry pointu) → stan użytkownika: disabled = `ACCOUNT_DISABLED`, `iat < epoka` = `TOKEN_REVOKED` (oba pisze sam jako `ApiError`) → principal = **email jako String**. `shouldNotFilter`: login/register/refresh |
| `config/SecurityConfig` | stateless, CSRF off, `permitAll`: h2-console, `/api/v1/auth/**`, swagger; entry point = 401 `ACCESS_DENIED`; `DelegatingPasswordEncoder` (`{bcrypt}`) |
| `event/AuthEmailEventListener` | `@Async("authEmailExecutor")` + AFTER_COMMIT; generuje token i wysyła mail; przy resend **najpierw** epoka aktywacji (fail-closed: Redis padł = brak maila) |
| `jwt/config/*Properties` | `jwt.{access,refresh,activation,passwordreset}.{secret,validityInSeconds}`; w `test` ACCESS i ACTIVATION żyją **2 s** |

## Przepływy, które trzeba znać
- **Uprawnienia pochodzą z bazy, nie z tokena.** Claim `roles` w ACCESS jest wymagany przy generowaniu, ale filtr bierze `authorities` z `UserAuthStateProvider`. Zmiana ról działa więc od razu po eviction cache — bez nowego tokena.
- **Unieważnianie (GH-63)**: zdarzenia z `user/event` → `UserSecurityEventListener` (AFTER_COMMIT) → `revoke` = usunięcie refreshów + epoka sesji + eviction cache. Reaktywacja i zmiana ról robią tylko eviction (bez czyszczenia epoki).
- **Logout**: blacklist ACCESS + `revokeUsersTokens` (wszystkie refreshe użytkownika, czyli wylogowanie ze wszystkich urządzeń) + wyczyszczenie cookie.
- **Refresh**: cookie `refreshToken` (path `/api/v1/auth/refresh`, HttpOnly, Secure, SameSite=Strict, 7 dni) → nowa para tokenów. **Bez rotacji**: stary refresh zostaje ważny do TTL albo do wypchnięcia przez limit 5.
- `AppUserDetailsService` + bean `authenticationProvider` istnieją, ale `login` **nie** używa `AuthenticationManager` — weryfikuje hasło sam (`HashedPassword.matches`).

## Niezmienniki — nie psuć
- `login`: nieznany e-mail i złe hasło = ten sam `INVALID_CREDENTIALS`; hasło sprawdzane **zawsze i jako pierwsze**; `USER_NOT_ACTIVATED` dopiero po poprawnym haśle.
- Porównania epok są **ostre** (`<`); epoka aktywacji ucięta do sekund (inaczej świeży token z resendu byłby odrzucony — patrz pamięć `project_resend_activation_design`).
- Side effecty bezpieczeństwa są **AFTER_COMMIT**, więc metoda serwisu publikująca zdarzenie **musi** być `@Transactional` (inaczej zdarzenie nie dotrze).
- Nieuwierzytelniony = **401**, nigdy 403. Nowe publiczne endpointy muszą leżeć pod `/api/v1/auth/**`. Gdy mają działać bez nagłówka, a mogą dostać stary Bearer, rozważ dopisanie do `shouldNotFilter`.
- Fail-open (blacklist, epoka) vs fail-closed (resend aktywacji, zapis refreshu) to świadome decyzje — nie ujednolicaj ich bez rozmowy.

## Testy, które to pinują
`auth/controller/AuthControllerTest`, `auth/service/AuthServiceImplTest`, `AuthServiceRollbackTest` (rollback = brak maila), `auth/jwt/service/*Test` (`RedisSessionEpochStoreFailureTest` = fail-open), `auth/event/AuthEmailEventListenerTest`, a przekrojowo pakiet `security/`: `AccessTokenInvalidationTest`, `ForceLogoutTest`, `PasswordChangeInvalidatesSessionsTest`, `RefreshTokenRevocationTest`, `UserAuthStateCacheTest`, `SecurityIntegrationTest`. Testy generują tokeny wprost przez `jwtService.generateToken(...)` i robią `flushAll()` na embedded Redis w `@BeforeEach`.

## Obserwacje z analizy kodu (2026-11-21) — zgłoszone jako issues, nie naprawiaj samodzielnie
1. **[#149] Komunikaty błędów zawierają dane wejściowe** — `GlobalExceptionHandler.buildResponse` kopiuje `ex.getMessage()` do `ApiError`, więc klient dostaje np. `"Invalid credentials for: jo***@example.com"` albo `"Username already exists: <username>"`. To nie jest wyrocznia enumeracji (obie gałęzie loginu mają ten sam kształt, a echo dotyczy własnego wejścia wołającego), ale stoi w sprzeczności z regułą "static messages", którą przyjęto dla wyszukiwarki (GH-122).
2. **[#145] Blacklistowany token resetu hasła dostaje komunikat o tokenie aktywacyjnym** (`AuthController.confirmPasswordReset`: `"Activation token has already been used…"`) — skopiowany tekst.
3. **[#145] Starsze linki resetu hasła nie są unieważniane.** Aktywacja ma epokę (resend unieważnia stare linki), reset hasła nie: kilka żądań daje kilka ważnych tokenów (15 min). Po udanym resecie *inny*, nieużyty link nadal pozwala ustawić hasło jeszcze raz, bo `confirm` nie sprawdza żadnej epoki.
4. **[#146] Brak rotacji refresh tokenów i wykrywania ich ponownego użycia** (patrz "Refresh" wyżej). Skradziony refresh żyje do 7 dni albo do wylogowania.
5. **[#148] Logout przy awarii Redis**: zapis do blacklisty jest połykany, a potem `revokeAllByUserEmail` rzuca `REDIS_UNAVAILABLE` — klient dostaje 503, cookie nie jest czyszczone, a access token żyje dalej (≤15 min).
6. [#150] `RedisRefreshTokenStore` loguje **pełny e-mail** na poziomie ERROR; reszta modułu używa `obfuscated()`.
