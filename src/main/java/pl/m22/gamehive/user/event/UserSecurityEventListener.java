package pl.m22.gamehive.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserSecurityEventListener {

    private final RedisRefreshTokenStore refreshTokenStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeactivated(UserDeactivatedEvent event) {

        revoke(event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserDeleted(UserDeletedEvent event) {

        revoke(event.email());
    }

    private void revoke(String email) {

        refreshTokenStore.revokeAllByUserEmail(email);
    }
}
