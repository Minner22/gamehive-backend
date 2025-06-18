package pl.m22.gamehive.auth.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;

public interface AuthService {

    void register(RegistrationDto registrationDto);
    CredentialsDto login(LoginDto loginDto);
    void activateUser(String email);
}
