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

@Slf4j
@RequiredArgsConstructor
@Component
public class AuthEmailEventListener {

    private final JwtService jwtService;
    private final MailService mailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {

        String token = jwtService.generateToken(event.email(), JwtTokenType.ACTIVATION, null);

        try {
            mailService.sendActivationEmail(event.email(), token);
        } catch (Exception e) {
            log.error("Failed to send activation email to {}: {}", LoggingUtils.obfuscateEmail(event.email()), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {

        String token = jwtService.generateToken(event.email(), JwtTokenType.PASSWORD_RESET, null);

        try {
            mailService.sendPasswordResetEmail(event.email(), token);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", LoggingUtils.obfuscateEmail(event.email()), e.getMessage());
        }
    }
}
