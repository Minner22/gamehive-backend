package pl.m22.gamehive.common.email.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pl.m22.gamehive.common.email.config.MailProperties;

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

        mailSender.send(mailMessage);
    }
}
