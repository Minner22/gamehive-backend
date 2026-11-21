package pl.m22.gamehive.user.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.config.CacheConfig;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("onUserCredentialsChanged() -> epoka resetu ustawiona, nawet gdy rewokacja refreshy padnie")
    void onUserCredentialsChanged_resetEpochSurvivesRevokeFailure() {
        String email = "jane.smith@example.com";
        doThrow(new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "down"))
                .when(refreshTokenStore).revokeAllByUserEmail(email);

        assertThatThrownBy(() -> listener.onUserCredentialsChanged(new UserCredentialsChangedEvent(email)))
                .isInstanceOf(InfrastructureException.class);
        verify(sessionEpochStore).invalidatePasswordResetNow(email);
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
