package pl.m22.gamehive.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.email.service.MailService;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.util.function.BiConsumer;

@Slf4j
@RequiredArgsConstructor
@Component
public class AuthEmailEventListener {

    private final JwtService jwtService;
    private final MailService mailService;
    private final RedisSessionEpochStore sessionEpochStore;

    @Async("authEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {

        dispatch(event.email(), JwtTokenType.ACTIVATION, mailService::sendActivationEmail, "activation");
    }

    @Async("authEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {

        dispatch(event.email(), JwtTokenType.PASSWORD_RESET, mailService::sendPasswordResetEmail, "password reset");
    }

    @Async("authEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivationEmailResendRequested(ActivationEmailResendRequestedEvent event) {

        // Epokę ustawiamy PRZED wygenerowaniem tokenu (poza try dispatch): świeży token dostanie iat >= epoki
        // i pozostanie ważny, a każdy starszy token aktywacyjny zostanie unieważniony.
        // Fail-closed: gdy invalidateActivationNow rzuci (Redis down), dispatch się nie wykona -> brak resendu,
        // bo nie chcemy wysłać nowego linku nie mogąc unieważnić starego.
        sessionEpochStore.invalidateActivationNow(event.email());
        dispatch(event.email(), JwtTokenType.ACTIVATION, mailService::sendActivationEmail, "activation");
    }

    private void dispatch(String email, JwtTokenType tokenType, BiConsumer<String, String> sender, String emailKind) {

        try {
            String token = jwtService.generateToken(email, tokenType, null);
            sender.accept(email, token);
        } catch (Exception e) {
            log.error("Failed to send {} email to {}: {}", emailKind, LoggingUtils.obfuscateEmail(email), e.getMessage());
        }
    }
}