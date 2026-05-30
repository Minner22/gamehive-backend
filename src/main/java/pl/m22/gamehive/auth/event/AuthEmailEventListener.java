package pl.m22.gamehive.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.common.email.service.MailService;
import pl.m22.gamehive.common.logging.LoggingUtils;

import java.util.function.BiConsumer;

@Slf4j
@RequiredArgsConstructor
@Component
public class AuthEmailEventListener {

    private final JwtService jwtService;
    private final MailService mailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {

        dispatch(event.email(), JwtTokenType.ACTIVATION, mailService::sendActivationEmail, "activation");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {

        dispatch(event.email(), JwtTokenType.PASSWORD_RESET, mailService::sendPasswordResetEmail, "password reset");
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