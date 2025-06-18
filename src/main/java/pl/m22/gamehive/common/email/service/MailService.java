package pl.m22.gamehive.common.email.service;

public interface MailService {
    void sendActivationEmail(String email, String activationToken);
}
