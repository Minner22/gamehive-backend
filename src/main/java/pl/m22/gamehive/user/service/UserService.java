package pl.m22.gamehive.user.service;

import pl.m22.gamehive.user.dto.UserCredentialsDto;
import pl.m22.gamehive.user.dto.UserRegistrationDto;

import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<UserCredentialsDto> findCredentialsByEmail(String email);
    List<String> findAllUserEmails();
    void deleteUserByEmail(String email);
    void register(UserRegistrationDto registrationDto);
    boolean emailExists(String email);
    boolean usernameExists(String username);
}
