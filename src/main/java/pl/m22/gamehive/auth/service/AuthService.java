package pl.m22.gamehive.auth.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.model.AppUser;

public interface AuthService {

    void register(RegistrationDto registrationDto);

    String generateActivationToken(AppUser appUser);

    CredentialsDto login(LoginDto loginDto);

    void activateUser(String email);

    void requestPasswordReset(String email);

    void confirmPasswordReset(String token, String newPassword);

    AppUser registerUser(RegistrationDto registrationDto);
}
