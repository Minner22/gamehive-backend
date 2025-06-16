package pl.m22.gamehive.user.service;

import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.dto.LoginDto;
import pl.m22.gamehive.auth.dto.RegistrationDto;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<CredentialsDto> findCredentialsByEmail(String email);
    List<String> findAllUserEmails();
    void deleteUserByEmail(String email);

    boolean emailExists(String email);
    boolean usernameExists(String username);
    List<AppUser> findAllUsers();

}
