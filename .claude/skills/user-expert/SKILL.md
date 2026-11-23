---
name: user-expert
description: Ekspert modułu user GameHive (pl.m22.gamehive.user) — AppUser (UUID v7, value objects, role EAGER, profil), self-service /api/v1/users/me (profil, usunięcie własnego konta), panel admina /api/v1/admin/users/** (role, deaktywacja, aktywacja, delete, force-logout), guardy własnego konta i ostatniego admina, audyt kont (UserAuditEvent → user_audit_log, GET /api/v1/admin/audit), zdarzenia bezpieczeństwa GH-63. Użyj przy każdym pytaniu o użytkowników, role, usuwanie kont lub audyt kont, przy planowaniu, review i debugowaniu zmian w user/, oraz gdy nowa funkcja musi zareagować na usunięcie/deaktywację użytkownika.
---

# Ekspert: `user/`

Jak pracować: odpowiadaj z kodu (`plik:linia`). Szczegóły audytu i GH-63 są w CLAUDE.md, w sekcjach "Security & JWT pipeline" i "Audit log".

## Mapa kodu
| Klasa | Odpowiedzialność |
|---|---|
| `model/AppUser` | `UuidEntity` (v7, id nadawane przy `persist()`); `Username`/`HashedPassword`/`Email` jako `@Embedded` VO; role `@ManyToMany(EAGER)` przez `user_roles`; `UserProfile` `@OneToOne(cascade = ALL)`; fabryka `register(...)` + mutatory `activate/deactivate/changePassword/assignRole/replaceRoles/attachProfile` |
| `model/UserProfile`, `Address` | profil (dane osobowe, adres jako embeddable od V5) |
| `model/UserRole` | `name` z prefiksem `ROLE_` (`ROLE_ADMIN`, `ROLE_MODERATOR`, `ROLE_USER`); seed w `V2` |
| `service/UserServiceImpl` | lookupy (`USER_NOT_FOUND`), mutacje admina z guardami, `deleteOwnAccount`, `updateCurrentUserProfile`; każda mutacja publikuje zdarzenie bezpieczeństwa **i** `UserAuditEvent` |
| `service/AppUserDetailsService`, `util/AppUserDetails` | adapter Spring Security, używany przez `UserAuthStateProvider` (stan per żądanie) |
| `service/AuditLogService` | `record` w `@Transactional(REQUIRES_NEW)` — osobny bean |
| `service/AuditLogQueryService` + `UserAuditLogSpecifications` + `dto/AuditLogFilter` | odczyt audytu z filtrami (GH-89) |
| `event/UserSecurityEventListener` | AFTER_COMMIT: revoke albo samo eviction. `revoke()` (GH-168): eviction cache → każdy krok Redis (refreshe, epoka sesji; przy zmianie hasła wcześniej epoka resetu) w `bestEffort` (łapie tylko `InfrastructureException`, log ERROR, idzie dalej) → **ponowne eviction w `finally`**, bo cache trzyma epokę sesji. Dzięki pierwszemu eviction deaktywacja/usunięcie działa od razu nawet przy padniętym Redis (filtr czyta `enabled=false` z bazy). Jedyny handler spoza AFTER_COMMIT: `onRefreshTokenReuseDetected` (GH-146) — synchroniczny `@EventListener`, bo `/refresh` nie ma transakcji; woła to samo `revoke()` |
| `event/AuditLogEventListener` | AFTER_COMMIT → `AuditLogService.record` |
| `controller/UserController` | `GET /me`, `PATCH /me/profile`, `DELETE /me` (hasło w body `DeleteAccountDto`, czyści cookie refresh) |
| `controller/AdminUserController` | `@PreAuthorize("hasRole('ADMIN')")`: lista, po id / username / email, `PUT /{id}/roles`, `PATCH /{id}/deactivate`, `PATCH /{id}/activate`, `DELETE /{id}`, `POST /{id}/force-logout`; wspólne 401/403/500 na poziomie klasy |
| `controller/AdminAuditController` | `GET /api/v1/admin/audit` (ADMIN) |

## Mutacje i ich skutki
| Operacja | Guardy | Zdarzenie bezpieczeństwa | Audyt |
|---|---|---|---|
| zmiana ról | własne konto, ostatni admin (gdy admin traci rolę) | `UserRolesUpdatedEvent` (eviction) | `ROLE_CHANGE` + JSON before/after |
| deaktywacja | własne konto, ostatni admin | `UserDeactivatedEvent` (revoke) | `DEACTIVATE` |
| aktywacja | — (requester tylko jako aktor) | `UserReactivatedEvent` (eviction, **epoka zostaje**) | `ACTIVATE` |
| delete przez admina | własne konto, ostatni admin | `UserDeletedEvent(email, userId)` (revoke + sprzątanie kolekcji) | `DELETE` |
| usunięcie własnego konta | poprawne hasło (`INVALID_PASSWORD`), ostatni admin | `UserDeletedEvent` | `DELETE` (aktor = cel) |
| force-logout | własne konto | `UserForceLoggedOutEvent` (revoke) | `FORCE_LOGOUT` |
| reset hasła (w `auth/`) | — | `UserCredentialsChangedEvent` (epoka resetu hasła — unieważnia pozostałe linki, GH-145 — potem revoke) | `PASSWORD_CHANGE` |

**Nowy moduł z danymi per-user** (jak `collection/`): UUID bez FK + listener na `UserDeletedEvent` (AFTER_COMMIT → osobny bean `REQUIRES_NEW`, bulk `@Modifying` delete). `UserDeletedEvent` niesie `userId`, bo po commicie wiersza już nie ma.

## Niezmienniki
- `force-logout` ma `@Transactional(readOnly = true)` **celowo**: bez transakcji AFTER_COMMIT by nie odpalił (pamięć `project_force_logout_design`).
- Audyt: brak FK, wartości kopiowane; dokładnie jeden wpis na zatwierdzoną akcję; błąd zapisu audytu nie cofa akcji.
- Wiersze `application_users` z `data.sql` mają stałe UUID (`support/SeededUsers`). Testy non-`@Transactional` nie kasują seedowanych użytkowników — tworzą własnych.
- Usunięcie konta jest twarde (`userRepository.delete`) — treści i audyt przeżywają je dzięki braku FK.

## Testy
`user/controller/{UserControllerTest, AdminUserControllerTest, AdminAuditControllerTest, DeleteOwnAccountTest}`, `user/service/{UserServiceImplTest, AuditLogServiceTest, AuditLogQueryServiceTest}`, `user/event/*`, `user/model/{AppUserTest, AppUserUuidGenerationTest}`, `user/repository/*` (VO w zapytaniach: `UserRepositoryVoTest`), a przekrojowo `security/*` i `collection/event/CollectionUserCleanupTest`.

## Obserwacje z analizy kodu (2026-11-21) — zgłoszone jako issues, nie naprawiaj samodzielnie
1. **[#147] Wyścig na guardzie "ostatni admin"** — `guardLastAdmin` liczy adminów (`countByRoles_Name`) bez blokady. Dwóch ostatnich adminów usuwających/deaktywujących się **nawzajem** w tym samym momencie: obaj widzą licznik 2, obie operacje przechodzą, system zostaje bez admina. Guard własnego konta tego nie łapie, bo każdy działa na *innym* koncie. Mało prawdopodobne, ale nieodwracalne bez SQL.
2. ~~[#144] Długi `X-Correlation-Id` gubi wpis audytu~~ — **rozwiązane w GH-144**: filtr podmienia niepoprawny nagłówek na UUID, więc insert do `correlation_id VARCHAR(64)` już nie pada. Lekcja zostaje aktualna: **każda** wartość kontrolowana przez klienta, która trafia do kolumny audytu, musi zmieścić się w kolumnie, zanim zostanie opublikowana. Błąd insertu w `REQUIRES_NEW` po AFTER_COMMIT jest tylko logowany, a wpis znika po cichu. Regresję pilnuje `common/logging/CorrelationIdAuditRegressionTest`.
3. [#150] `updateCurrentUserProfile` nie ma `@Transactional` (opiera się na `save`). Dziś działa poprawnie, ale nie pasuje do reszty serwisu.
