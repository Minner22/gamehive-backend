package pl.m22.gamehive.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserService {
    @Deprecated
    Optional<CredentialsDto> findCredentialsByEmail(String email);

    @Deprecated
    List<String> findAllUserEmails();

    @Deprecated
    void deleteUserByEmail(String email);

    @Deprecated
    boolean emailExists(String email);

    @Deprecated
    boolean usernameExists(String username);

    AppUser findUserByEmail(Email email);
    AppUser findUserById(Long id);
    AppUser findUserByUsername(Username username);

    Page<AppUser> findAllUsers(Pageable pageable);

    AppUser updateUserRoles(Long userId, Set<String> roleNames, Email requesterEmail);
    AppUser deactivateUser(Long userId, Email requesterEmail);
    AppUser activateUser(Long userId);
    void deleteUser(Long userId, Email requesterEmail);

    UserProfile updateCurrentUserProfile(Email email, UserProfileUpdateDto userProfileUpdateDto);

}
