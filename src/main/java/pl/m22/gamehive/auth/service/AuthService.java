package pl.m22.gamehive.auth.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.common.domain.Email;

public interface AuthService {

    void register(RegistrationDto registrationDto);

    CredentialsDto login(LoginDto loginDto);

    void activateUser(Email email);

    void requestPasswordReset(Email email);

    void confirmPasswordReset(Email email, String newPassword);

    void resendActivationEmail(Email email);
}
