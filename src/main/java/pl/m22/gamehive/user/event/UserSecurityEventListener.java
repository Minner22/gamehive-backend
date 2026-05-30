package pl.m22.gamehive.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.config.CacheConfig;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserSecurityEventListener {

    private final RedisRefreshTokenStore refreshTokenStore;
    private final RedisSessionEpochStore sessionEpochStore;
    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeactivated(UserDeactivatedEvent event) {

        revoke(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeleted(UserDeletedEvent event) {

        revoke(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserReactivated(UserReactivatedEvent event) {

        evictAuthState(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserRolesUpdated(UserRolesUpdatedEvent event) {

        evictAuthState(event.email());
    }

    private void revoke(String email) {

        refreshTokenStore.revokeAllByUserEmail(email);
        sessionEpochStore.invalidateNow(email);
        evictAuthState(email);
    }

    private void evictAuthState(String email) {

        Cache cache = cacheManager.getCache(CacheConfig.USER_AUTH_STATE);

        if (cache != null) {
            cache.evict(email);
        }
    }
}
