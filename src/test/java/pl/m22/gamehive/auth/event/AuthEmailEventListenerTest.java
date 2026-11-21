package pl.m22.gamehive.auth.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.m22.gamehive.auth.jwt.JwtTokenType;
import pl.m22.gamehive.auth.jwt.service.JwtService;
import pl.m22.gamehive.auth.jwt.service.RedisSessionEpochStore;
import pl.m22.gamehive.common.email.service.MailService;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthEmailEventListenerTest {

    private static final String EMAIL = "user@test.com";

    @Mock JwtService jwtService;
    @Mock MailService mailService;
    @Mock RedisSessionEpochStore sessionEpochStore;
    @InjectMocks AuthEmailEventListener listener;

    @Test
    @DisplayName("onUserRegistered() -> generuje token ACTIVATION i wysyła mail aktywacyjny")
    void onUserRegistered_sendsActivationEmail() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null)).thenReturn("activation-token");

        listener.onUserRegistered(new UserRegisteredEvent(EMAIL));

        verify(mailService).sendActivationEmail(EMAIL, "activation-token");
    }

    @Test
    @DisplayName("onUserRegistered() -> błąd wysyłki maila nie propaguje wyjątku")
    void onUserRegistered_mailFails_doesNotPropagate() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null)).thenReturn("activation-token");
        doThrow(new RuntimeException("SMTP down")).when(mailService).sendActivationEmail(eq(EMAIL), any());

        assertDoesNotThrow(() -> listener.onUserRegistered(new UserRegisteredEvent(EMAIL)));
    }

    @Test
    @DisplayName("onUserRegistered() -> błąd generowania tokena nie propaguje i nie wysyła maila (regresja F1)")
    void onUserRegistered_tokenGenerationFails_doesNotPropagateNorSend() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null)).thenThrow(new RuntimeException("signing error"));

        assertDoesNotThrow(() -> listener.onUserRegistered(new UserRegisteredEvent(EMAIL)));

        verify(mailService, never()).sendActivationEmail(any(), any());
    }

    @Test
    @DisplayName("onPasswordResetRequested() -> generuje token PASSWORD_RESET i wysyła mail resetu")
    void onPasswordResetRequested_sendsResetEmail() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null)).thenReturn("reset-token");

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(EMAIL));

        verify(mailService).sendPasswordResetEmail(EMAIL, "reset-token");
    }

    @Test
    @DisplayName("onPasswordResetRequested() -> błąd wysyłki maila nie propaguje wyjątku")
    void onPasswordResetRequested_mailFails_doesNotPropagate() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null)).thenReturn("reset-token");
        doThrow(new RuntimeException("SMTP down")).when(mailService).sendPasswordResetEmail(eq(EMAIL), any());

        assertDoesNotThrow(() -> listener.onPasswordResetRequested(new PasswordResetRequestedEvent(EMAIL)));
    }

    @Test
    @DisplayName("onPasswordResetRequested() -> błąd generowania tokena nie propaguje i nie wysyła maila")
    void onPasswordResetRequested_tokenGenerationFails_doesNotPropagateNorSend() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null)).thenThrow(new RuntimeException("signing error"));

        assertDoesNotThrow(() -> listener.onPasswordResetRequested(new PasswordResetRequestedEvent(EMAIL)));

        verifyNoInteractions(mailService);
    }

    @Test
    @DisplayName("onActivationEmailResendRequested() -> ustawia epokę PRZED tokenem i wysyła świeży mail aktywacyjny")
    void onActivationEmailResendRequested_invalidatesThenSends() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null)).thenReturn("fresh-activation-token");

        listener.onActivationEmailResendRequested(new ActivationEmailResendRequestedEvent(EMAIL));

        // kolejność jest istotna poprawnościowo: epoka -> generacja tokenu -> wysyłka
        InOrder inOrder = inOrder(sessionEpochStore, jwtService, mailService);
        inOrder.verify(sessionEpochStore).invalidateActivationNow(EMAIL);
        inOrder.verify(jwtService).generateToken(EMAIL, JwtTokenType.ACTIVATION, null);
        inOrder.verify(mailService).sendActivationEmail(EMAIL, "fresh-activation-token");
    }

    @Test
    @DisplayName("onActivationEmailResendRequested() -> błąd wysyłki maila nie propaguje wyjątku")
    void onActivationEmailResendRequested_mailFails_doesNotPropagate() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.ACTIVATION, null)).thenReturn("fresh-activation-token");
        doThrow(new RuntimeException("SMTP down")).when(mailService).sendActivationEmail(eq(EMAIL), any());

        assertDoesNotThrow(() -> listener.onActivationEmailResendRequested(new ActivationEmailResendRequestedEvent(EMAIL)));
    }

    // ---------- GH-145: epoka resetu hasła przy każdym żądaniu ----------

    @Test
    @DisplayName("onPasswordResetRequested() -> ustawia epokę resetu PRZED tokenem, potem wysyła mail (GH-145)")
    void onPasswordResetRequested_invalidatesOlderLinksThenSends() {

        when(jwtService.generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null)).thenReturn("reset-token");

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(EMAIL));

        // epoka -> token -> mail: świeży token ma iat >= epoki, więc przechodzi; każdy starszy link już nie
        InOrder inOrder = inOrder(sessionEpochStore, jwtService, mailService);
        inOrder.verify(sessionEpochStore).invalidatePasswordResetNow(EMAIL);
        inOrder.verify(jwtService).generateToken(EMAIL, JwtTokenType.PASSWORD_RESET, null);
        inOrder.verify(mailService).sendPasswordResetEmail(EMAIL, "reset-token");
    }

    @Test
    @DisplayName("onPasswordResetRequested() przy Redis down -> brak tokena i maila (fail-closed, GH-145)")
    void onPasswordResetRequested_redisDown_failsClosed() {

        doThrow(new InfrastructureException(ErrorCode.REDIS_UNAVAILABLE, "down"))
                .when(sessionEpochStore).invalidatePasswordResetNow(EMAIL);

        // wyjątek wychodzi z listenera (AFTER_COMMIT + @Async, więc endpoint i tak odpowiedział już 200),
        // a nowy link nie powstaje, skoro nie dało się unieważnić starszych
        assertThrows(InfrastructureException.class,
                () -> listener.onPasswordResetRequested(new PasswordResetRequestedEvent(EMAIL)));
        verifyNoInteractions(jwtService, mailService);
    }
}
