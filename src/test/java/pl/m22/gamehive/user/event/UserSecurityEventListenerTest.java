package pl.m22.gamehive.user.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pl.m22.gamehive.auth.event.RefreshTokenReuseDetectedEvent;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.auth.jwt.service.UserAuthState;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.config.CacheConfig;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSecurityEventListenerTest {

    @Mock RedisRefreshTokenStore refreshTokenStore;
    @Mock RedisSessionEpochStore sessionEpochStore;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    @InjectMocks UserSecurityEventListener listener;

    @Test
    @DisplayName("onUserForceLoggedOut() -> rewokacja refresh + epoch + eviction cache userAuthState")
    void onUserForceLoggedOut_revokesEverything() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email));

        verify(refreshTokenStore).revokeAllByUserEmail(email);
        verify(sessionEpochStore).invalidateNow(email);
        verify(cache, times(2)).evict(email);   // przed krokami Redis i po zapisie epoki
    }

    // Inwariant: reaktywacja i zmiana ról to evict-only — NIE wolno im rewokować refresh ani zerować epoch
    // (inaczej reaktywacja wskrzeszałaby stare tokeny). Integracja pilnuje tylko eviction/utrzymanego epoch;
    // tu domykamy stronę negatywną na poziomie jednostki.

    @Test
    @DisplayName("onUserReactivated() -> tylko eviction cache, BEZ rewokacji refresh/epoch")
    void onUserReactivated_onlyEvictsCache() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onUserReactivated(new UserReactivatedEvent(email));

        verify(cache).evict(email);
        verifyNoInteractions(refreshTokenStore, sessionEpochStore);
    }

    @Test
    @DisplayName("onUserRolesUpdated() -> tylko eviction cache, BEZ rewokacji refresh/epoch")
    void onUserRolesUpdated_onlyEvictsCache() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onUserRolesUpdated(new UserRolesUpdatedEvent(email));

        verify(cache).evict(email);
        verifyNoInteractions(refreshTokenStore, sessionEpochStore);
    }

    // ---------- GH-145: zmiana hasła unieważnia wcześniej wystawione linki resetu ----------

    @Test
    @DisplayName("onUserCredentialsChanged() -> epoka resetu hasła, potem pełna rewokacja sesji (GH-145)")
    void onUserCredentialsChanged_invalidatesResetLinksThenRevokes() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email));

        InOrder inOrder = inOrder(sessionEpochStore, refreshTokenStore);
        inOrder.verify(sessionEpochStore).invalidatePasswordResetNow(email);
        inOrder.verify(refreshTokenStore).revokeAllByUserEmail(email);
        verify(sessionEpochStore).invalidateNow(email);
        verify(cache, times(2)).evict(email);   // przed krokami Redis i po zapisie epoki
    }

    @Test
    @DisplayName("onUserCredentialsChanged(): awaria rewokacji refreshy NIE przerywa epoki sesji ani czyszczenia cache (GH-168)")
    void onUserCredentialsChanged_revokeFailure_remainingStepsStillRun() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(redisDown()).when(refreshTokenStore).revokeAllByUserEmail(email);

        assertThatCode(() -> listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email)))
                .doesNotThrowAnyException();

        verify(sessionEpochStore).invalidatePasswordResetNow(email);
        verify(sessionEpochStore).invalidateNow(email);
        verify(cache, times(2)).evict(email);   // przed krokami Redis i po zapisie epoki
    }

    @Test
    @DisplayName("onUserCredentialsChanged(): awaria epoki resetu NIE blokuje rewokacji sesji (GH-168, wcześniej przerywała wszystko)")
    void onUserCredentialsChanged_resetEpochFailure_revokeStillRuns() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(redisDown()).when(sessionEpochStore).invalidatePasswordResetNow(email);

        assertThatCode(() -> listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email)))
                .doesNotThrowAnyException();

        verify(cache, times(2)).evict(email);   // przed krokami Redis i po zapisie epoki
        verify(refreshTokenStore).revokeAllByUserEmail(email);
        verify(sessionEpochStore).invalidateNow(email);
    }

    @Test
    @DisplayName("Deaktywacja przy niedostępnym Redis -> cache czyszczony NAJPIERW (filtr czyta enabled=false z bazy), reszta best-effort")
    void onUserDeactivated_redisDown_evictsCacheFirst() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(redisDown()).when(refreshTokenStore).revokeAllByUserEmail(email);
        doThrow(redisDown()).when(sessionEpochStore).invalidateNow(email);

        assertThatCode(() -> listener.onUserDeactivated(new UserDeactivatedEvent(email))).doesNotThrowAnyException();

        // czyszczenie PRZED (deaktywacja działa od razu przy padniętym Redis) i PO zapisie epoki (security review GH-168)
        InOrder inOrder = inOrder(cache, refreshTokenStore, sessionEpochStore);
        inOrder.verify(cache).evict(email);
        inOrder.verify(refreshTokenStore).revokeAllByUserEmail(email);
        inOrder.verify(sessionEpochStore).invalidateNow(email);
        inOrder.verify(cache).evict(email);
    }

    @Test
    @DisplayName("bestEffort łapie TYLKO InfrastructureException: błąd w naszym kodzie leci dalej")
    void bestEffort_doesNotSwallowProgrammingErrors() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(new IllegalStateException("bug")).when(refreshTokenStore).revokeAllByUserEmail(email);

        assertThatThrownBy(() -> listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email)))
                .isInstanceOf(IllegalStateException.class);
        verify(cache, times(2)).evict(email);   // przed krokami i w finally — także przy błędzie w naszym kodzie
    }

    @Test
    @DisplayName("Nieudany krok -> log ERROR z nazwą kroku i zamaskowanym e-mailem (bez pełnego adresu)")
    void bestEffort_logsStepWithObfuscatedEmail() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(redisDown()).when(sessionEpochStore).invalidateNow(email);
        Logger logger = (Logger) LoggerFactory.getLogger(UserSecurityEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("[session epoch]").doesNotContain(email);
            assertThat(e.getThrowableProxy()).isNotNull();
        });
    }

    private static InfrastructureException redisDown() {
        return new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "Cannot set epoch - Redis unavailable");
    }

    @Test
    @DisplayName("deaktywacja / usunięcie / force-logout -> NIE ruszają epoki resetu hasła (to nie zmiana hasła)")
    void otherRevocations_doNotTouchPasswordResetEpoch() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onUserDeactivated(new UserDeactivatedEvent(email));
        listener.onUserDeleted(new UserDeletedEvent(email, UUID.randomUUID()));
        listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email));

        verify(sessionEpochStore, never()).invalidatePasswordResetNow(anyString());
    }

    // ---------- GH-146: ponowne użycie zrotowanego refresh tokena ----------

    @Test
    @DisplayName("GH-146: RefreshTokenReuseDetectedEvent -> pełna rewokacja jak force-logout (refreshe + epoka + podwójna eviction)")
    void onRefreshTokenReuseDetected_revokesEverything() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);

        listener.onRefreshTokenReuseDetected(new RefreshTokenReuseDetectedEvent(email));

        verify(refreshTokenStore).revokeAllByUserEmail(email);
        verify(sessionEpochStore).invalidateNow(email);
        verify(sessionEpochStore, never()).invalidatePasswordResetNow(email);
        verify(cache, times(2)).evict(email);
    }

    @Test
    @DisplayName("GH-146: reuse -> stan z cache załadowany w trakcie rewokacji (bez epoki) nie przetrwa")
    void staleAuthStateLoadedDuringReuseRevoke_isEvicted() {
        String email = "jane.smith@example.com";
        Cache realCache = new ConcurrentMapCache(CacheConfig.USER_AUTH_STATE);
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(realCache);
        UserAuthState staleState = new UserAuthState(true, List.of(new SimpleGrantedAuthority("ROLE_USER")), null);
        doAnswer(_ -> {
            realCache.put(email, staleState);
            return null;
        }).when(refreshTokenStore).revokeAllByUserEmail(email);

        listener.onRefreshTokenReuseDetected(new RefreshTokenReuseDetectedEvent(email));

        assertThat(realCache.get(email)).isNull();
    }

    @Test
    @DisplayName("GH-146: listener reuse to synchroniczny @EventListener — @TransactionalEventListener nie odpaliłby bez transakcji")
    void onRefreshTokenReuseDetected_isPlainEventListener() throws Exception {
        java.lang.reflect.Method m = UserSecurityEventListener.class
                .getDeclaredMethod("onRefreshTokenReuseDetected", RefreshTokenReuseDetectedEvent.class);

        assertThat(m.isAnnotationPresent(org.springframework.context.event.EventListener.class)).isTrue();
        assertThat(m.isAnnotationPresent(org.springframework.transaction.event.TransactionalEventListener.class)).isFalse();
    }

    // ---------- security review GH-168: cache trzyma epokę sesji ----------

    @Test
    @DisplayName("Żądanie przeładowujące cache MIĘDZY czyszczeniem a zapisem epoki nie zostawia starej epoki na 60 s (force-logout)")
    void staleAuthStateLoadedDuringRevoke_isEvictedAfterEpochWrite() {
        String email = "jane.smith@example.com";
        Cache realCache = new ConcurrentMapCache(CacheConfig.USER_AUTH_STATE);
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(realCache);
        UserAuthState staleState = new UserAuthState(true, List.of(new SimpleGrantedAuthority("ROLE_USER")), null);
        // symulacja: równoległe żądanie atakującego trafia w okno po pierwszym czyszczeniu i — zanim
        // invalidateNow zapisze epokę — ładuje do cache stan BEZ epoki (UserAuthStateProvider czyta ją z Redisa)
        doAnswer(_ -> {
            realCache.put(email, staleState);
            return null;
        }).when(refreshTokenStore).revokeAllByUserEmail(email);

        listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email));

        // bez drugiego czyszczenia stary stan przetrwałby do TTL (60 s) i skradziony token nadal by działał
        assertThat(realCache.get(email)).isNull();
        verify(sessionEpochStore).invalidateNow(email);
    }

    @Test
    @DisplayName("To samo przy zmianie hasła: stary stan załadowany w trakcie rewokacji nie przetrwa listenera")
    void staleAuthStateLoadedDuringPasswordChange_isEvicted() {
        String email = "jane.smith@example.com";
        Cache realCache = new ConcurrentMapCache(CacheConfig.USER_AUTH_STATE);
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(realCache);
        doAnswer(_ -> {
            realCache.put(email, new UserAuthState(true, List.of(), null));
            return null;
        }).when(refreshTokenStore).revokeAllByUserEmail(email);

        listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email));

        assertThat(realCache.get(email)).isNull();
    }
}
