package pl.m22.gamehive.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.auth.event.RefreshTokenReuseDetectedEvent;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;
import pl.m22.gamehive.config.CacheConfig;

/**
 * Unieważnia sesje usera w reakcji na zmiany jego stanu/credentiali. Wszystkie handlery są AFTER_COMMIT —
 * efekty uboczne (Redis, cache) odpalają się tylko, gdy zmiana w DB faktycznie się scommitowała
 * (rollback => brak unieważnienia).
 */
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

    // Reaktywacja i zmiana ról tylko eksmitują cache — NIE czyszczą epoch ani nie ruszają refresh tokenów.
    // Dzięki temu reaktywacja nie wskrzesza starych tokenów (user musi zalogować się od nowa).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserReactivated(UserReactivatedEvent event) {

        evictAuthState(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserRolesUpdated(UserRolesUpdatedEvent event) {

        evictAuthState(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserCredentialsChanged(UserCredentialsChangedEvent event) {

        bestEffort("password reset epoch", event.email(), () -> sessionEpochStore.invalidatePasswordResetNow(event.email()));
        revoke(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserForceLoggedOut(UserForceLoggedOutEvent event) {

        revoke(event.email());
    }

    @EventListener
    void onRefreshTokenReuseDetected(RefreshTokenReuseDetectedEvent event) {

        revoke(event.email());
    }

    private void revoke(String email) {

        evictAuthState(email);

        try {
            bestEffort("revoke refresh tokens", email, () -> refreshTokenStore.revokeAllByUserEmail(email));
            bestEffort("session epoch", email, () -> sessionEpochStore.invalidateNow(email));
        } finally {
            evictAuthState(email);
        }
    }

    // Eviction programowa (nie @CacheEvict): metody listenera są package-private i wołane przez
    // infrastrukturę zdarzeń, więc interceptcja przez proxy AOP cache'a nie jest gwarantowana.
    private void evictAuthState(String email) {

        Cache cache = cacheManager.getCache(CacheConfig.USER_AUTH_STATE);

        if (cache != null) {
            cache.evict(email);
        }
    }

    private void bestEffort(String step, String email, Runnable action) {

        try {
            action.run();
        } catch (InfrastructureException e) {
            log.error("Security side effect [{}] failed for user {} - continuing with the remaining steps",
                    step, LoggingUtils.obfuscateEmail(email), e);
        }
    }
}
