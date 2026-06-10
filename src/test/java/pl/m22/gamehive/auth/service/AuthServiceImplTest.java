package pl.m22.gamehive.auth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// NOTE: this class must NOT be @Transactional — verify(mailSender) relies on register()/requestPasswordReset()
// actually committing so the @TransactionalEventListener(AFTER_COMMIT) fires. A test-level @Transactional would
// roll back, the listener would never run, and the mail verifications would fail misleadingly.
// The listener is also @Async("authEmailExecutor"); under the "test" profile that bean is a SyncTaskExecutor
// (TestAsyncConfig), so the dispatch runs inline on the caller thread and verify(mailSender) stays deterministic.
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceImplTest {

    private static final String NEW_USER_EMAIL = "newuser@test.com";
    private static final String RESEND_EMAIL = "resend_svc@test.com";

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @MockitoBean JavaMailSender mailSender;

    @AfterEach
    void deleteNewUsers() {

        userRepository.findByEmail(NEW_USER_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(RESEND_EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("register() happy path -> użytkownik zapisany, mail wysłany")
    void register_happy_path() {

        RegistrationDto dto = new RegistrationDto("newuser", NEW_USER_EMAIL, "password123");

        authService.register(dto);

        assertTrue(userRepository.existsByEmail(NEW_USER_EMAIL));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("register() błąd SMTP -> użytkownik dalej zapisany, ale mail nie wysłany")
    void register_mail_fails_user_still_saved() {

        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        RegistrationDto dto = new RegistrationDto("failuser", NEW_USER_EMAIL, "password123");

        assertDoesNotThrow(() -> authService.register(dto));

        assertTrue(userRepository.existsByEmail(NEW_USER_EMAIL));
        // proves the AFTER_COMMIT listener fired and the SMTP failure was swallowed (not skipped)
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("requestPasswordReset() istniejący email -> mail wysłany")
    void requestPasswordReset_existingEmail_sendsMail() {

        authService.requestPasswordReset(new Email("john.doe@example.com"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("requestPasswordReset() nieistniejący email -> mail NIE wysłany (anty-enumeracja)")
    void requestPasswordReset_nonExistingEmail_doesNotSendMail() {

        authService.requestPasswordReset(new Email("nobody@test.com"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("requestPasswordReset() błąd SMTP -> brak wyjątku do usera (istniejący email)")
    void requestPasswordReset_mail_fails_no_error_to_user() {

        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> authService.requestPasswordReset(new Email("john.doe@example.com")));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("register() duplikat email -> EMAIL_ALREADY_EXISTS")
    void register_duplicate_email() {

        RegistrationDto dto = new RegistrationDto("other", "john.doe@example.com", "password123");

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("register() duplikat username -> USERNAME_ALREADY_EXISTS")
    void register_duplicate_username() {

        RegistrationDto dto = new RegistrationDto("john_doe", NEW_USER_EMAIL, "password123");

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS);

    }

    @Test
    @DisplayName("resendActivationEmail() nieaktywne konto -> mail wysłany")
    void resendActivationEmail_inactiveUser_sendsMail() {

        authService.register(new RegistrationDto("resend_svc", RESEND_EMAIL, "password123"));
        clearInvocations(mailSender);   // pomiń mail z rejestracji

        authService.resendActivationEmail(new Email(RESEND_EMAIL));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("resendActivationEmail() aktywne konto -> mail NIE wysłany")
    void resendActivationEmail_activeUser_doesNotSendMail() {

        // john.doe@example.com jest seedowany w data.sql jako aktywny (loguje się w innych testach)
        authService.resendActivationEmail(new Email("john.doe@example.com"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("resendActivationEmail() nieistniejące konto -> mail NIE wysłany (anty-enumeracja)")
    void resendActivationEmail_nonExistingUser_doesNotSendMail() {

        authService.resendActivationEmail(new Email("nobody@test.com"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}