package pl.m22.gamehive.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

import java.util.Set;
import java.util.UUID;

public interface UserService {

    AppUser findUserByEmail(Email email);
    AppUser findUserById(UUID id);
    AppUser findUserByUsername(Username username);

    Page<AppUser> findAllUsers(Pageable pageable);

    AppUser updateUserRoles(UUID userId, Set<String> roleNames, Email requesterEmail);
    AppUser deactivateUser(UUID userId, Email requesterEmail);
    AppUser activateUser(UUID userId, Email requesterEmail);
    void deleteUser(UUID userId, Email requesterEmail);

    void forceLogoutUser(UUID userId, Email requesterEmail);

    UserProfile updateCurrentUserProfile(Email email, UserProfileUpdateDto userProfileUpdateDto);

}
