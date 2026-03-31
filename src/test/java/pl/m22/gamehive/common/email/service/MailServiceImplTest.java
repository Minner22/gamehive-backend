package pl.m22.gamehive.common.email.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class MailServiceImplTest {

    @Autowired
    MailService mailService;

    @MockBean
    JavaMailSender mailSender;

    @Test
    @DisplayName("sendActivationEmail when SMTP throws exception -> InfrastructureException EMAIL_SEND_FAILED")
    void sendActivationEmail_smtpFailure_throwsInfrastructureException() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        InfrastructureException ex = assertThrows(InfrastructureException.class,
                () -> mailService.sendActivationEmail("user@example.com", "someToken"));

        assertEquals(ErrorCode.EMAIL_SEND_FAILED, ex.getErrorCode());
    }
}