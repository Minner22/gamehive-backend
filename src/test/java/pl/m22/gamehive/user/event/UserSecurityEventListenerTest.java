package pl.m22.gamehive.user.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.config.CacheConfig;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
