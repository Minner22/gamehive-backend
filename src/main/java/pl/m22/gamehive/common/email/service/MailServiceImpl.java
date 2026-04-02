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

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final MailProperties mailProperties;
    private final JavaMailSender mailSender;

    @Override
    public void sendActivationEmail(String email, String activationToken) {

        String activationLink = mailProperties.getActivationAddress() +
                "?token=" +
                activationToken;

        String emailContent = "Hello,\n\n" +
                "Please activate your account by clicking the link below:\n" +
                activationLink +
                "\n\nThank you!";


        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(mailProperties.getUsername());
        mailMessage.setTo(email);
        mailMessage.setSubject("Account Activation");
        mailMessage.setText(emailContent);

        try {
            mailSender.send(mailMessage);
        } catch (MailException e) {
            log.error("Failed to send activation email to {}: {}", email, e.getMessage());
            throw new InfrastructureException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    public void sendPasswordResetEmail(String email, String resetToken) {
        String activationLink = mailProperties.getPasswordResetAddress() +
                "?token=" +
                resetToken;

        String emailContent = "Hello,\n\n" +
                "Please reset your password by clicking the link below:\n" +
                activationLink +
                "\n\nThank you!";


        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(mailProperties.getUsername());
        mailMessage.setTo(email);
        mailMessage.setSubject("Password reset");
        mailMessage.setText(emailContent);

        try {
            mailSender.send(mailMessage);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}: {}", email, e.getMessage());
            throw new InfrastructureException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
