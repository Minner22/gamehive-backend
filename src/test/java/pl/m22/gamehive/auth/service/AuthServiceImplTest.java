package pl.m22.gamehive.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceImplTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @MockBean JavaMailSender mailSender;

    @Test
    @DisplayName("register() happy path -> użytkownik zapisany, mail wysłany")
    void register_happy_path() {
        RegistrationDto dto = new RegistrationDto("newuser", "newuser@test.com", "password123");

        authService.register(dto);

        assertTrue(userRepository.existsByEmail("newuser@test.com"));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("register() błąd SMTP -> rollback, użytkownik NIE zapisany")
    void register_email_fails_rollback() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));
        RegistrationDto dto = new RegistrationDto("failuser", "fail@test.com", "password123");

        assertThrows(InfrastructureException.class, () -> authService.register(dto));

        assertFalse(userRepository.existsByEmail("fail@test.com"));
    }

    @Test
    @DisplayName("register() duplikat email -> EMAIL_ALREADY_EXISTS")
    void register_duplicate_email() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> authService.register(new RegistrationDto("other", "john.doe@example.com", "password123")));
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("register() duplikat username -> USERNAME_ALREADY_EXISTS")
    void register_duplicate_username() {
        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> authService.register(new RegistrationDto("john_doe", "other@test.com", "password123")));
        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
    }
}