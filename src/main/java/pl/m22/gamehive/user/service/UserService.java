package pl.m22.gamehive.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserService {
    Optional<CredentialsDto> findCredentialsByEmail(String email);
    List<String> findAllUserEmails();
    void deleteUserByEmail(String email);

    boolean emailExists(String email);
    boolean usernameExists(String username);
    List<AppUser> findAllUsers();
    Optional<AppUser> findUserByEmail(String email);

    AppUser findUserById(Long id);
    AppUser findUserByUsername(String username);

    Page<AppUser> findAllUsers(Pageable pageable);

    UserProfile updateCurrentUserProfile(String email, UserProfileUpdateDto userProfileUpdateDto);

}
