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
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.config.CacheConfig;

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
        verify(cache).evict(email);
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
        verify(cache).evict(email);
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
        verify(cache).evict(email);
    }

    @Test
    @DisplayName("onUserCredentialsChanged(): awaria epoki resetu NIE blokuje rewokacji sesji (GH-168, wcześniej przerywała wszystko)")
    void onUserCredentialsChanged_resetEpochFailure_revokeStillRuns() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(redisDown()).when(sessionEpochStore).invalidatePasswordResetNow(email);

        assertThatCode(() -> listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email)))
                .doesNotThrowAnyException();

        verify(cache).evict(email);
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

        InOrder inOrder = inOrder(cache, refreshTokenStore, sessionEpochStore);
        inOrder.verify(cache).evict(email);
        inOrder.verify(refreshTokenStore).revokeAllByUserEmail(email);
        inOrder.verify(sessionEpochStore).invalidateNow(email);
    }

    @Test
    @DisplayName("bestEffort łapie TYLKO InfrastructureException: błąd w naszym kodzie leci dalej")
    void bestEffort_doesNotSwallowProgrammingErrors() {
        String email = "jane.smith@example.com";
        when(cacheManager.getCache(CacheConfig.USER_AUTH_STATE)).thenReturn(cache);
        doThrow(new IllegalStateException("bug")).when(refreshTokenStore).revokeAllByUserEmail(email);

        assertThatThrownBy(() -> listener.onUserForceLoggedOut(new UserForceLoggedOutEvent(email)))
                .isInstanceOf(IllegalStateException.class);
        verify(cache).evict(email);   // cache i tak już wyczyszczony — idzie pierwszy
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
}
