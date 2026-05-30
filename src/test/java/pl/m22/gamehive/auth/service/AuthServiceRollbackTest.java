package pl.m22.gamehive.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceRollbackTest {

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    JavaMailSender mailSender;

    @Test
    @DisplayName("rollback transakcji rejestracji -> mail NIE wysłany, user NIE zapisany")
    void rollback_does_not_send_mail() {

        RegistrationDto dto = new RegistrationDto("rollbackuser", "rollback@test.com", "password123");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(_ -> {
                    authService.register(dto);
                    throw new IllegalStateException("forced rollback after registration");
                })
        ).isInstanceOf(IllegalStateException.class);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertFalse(userRepository.existsByEmail("rollback@test.com"));
    }
}
