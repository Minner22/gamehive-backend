package pl.m22.gamehive.common.email.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.email.config.MailProperties;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.exception.InfrastructureException;
import pl.m22.gamehive.common.logging.LoggingUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final MailProperties mailProperties;
    private final JavaMailSender mailSender;

    @Override
    public void sendActivationEmail(String email, String activationToken) {

        String link = mailProperties.getActivationAddress() + "?token=" + activationToken;
        String body = "Hello,\n\nPlease activate your account by clicking the link below:\n" + link + "\n\nThank you!";

        sendEmail(email, "Account Activation", body);

        log.info("Activation email sent to: {}", LoggingUtils.obfuscateEmail(email));
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetToken) {

        String link = mailProperties.getPasswordResetAddress() + "?token=" + resetToken;
        String body = "Hello,\n\nPlease reset your password by clicking the link below:\n" + link + "\n\nThank you!";

        sendEmail(email, "Password reset", body);

        log.info("Password reset sent to: {}", LoggingUtils.obfuscateEmail(email));
    }

    private void sendEmail(String to, String subject, String text) {

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(mailProperties.getUsername());
        mailMessage.setTo(to);
        mailMessage.setSubject(subject);
        mailMessage.setText(text);

        try {
            mailSender.send(mailMessage);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new InfrastructureException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}