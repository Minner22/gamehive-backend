package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.dto.CredentialsDto;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.mapper.UserMapper;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_ROLE = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserRoleRepository userRoleRepository;
    private final RedisRefreshTokenStore redisRefreshTokenStore;

    @Override
    public Optional<CredentialsDto> findCredentialsByEmail(String email) {

        return userRepository.findByEmail(email)
                .map(userMapper::toCredentialsDto);
    }

    @Override
    public List<String> findAllUserEmails() {

        return userRepository.findAllUsersByRoles_Name(USER_ROLE).stream()
                .map(AppUser::getEmail)
                .toList();
    }

    @Transactional
    @Override
    public void deleteUserByEmail(String email) {
        userRepository.deleteByEmail(email);
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    public boolean usernameExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Override
    public AppUser findUserByEmail(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserById(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserByUsername(String username) {

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Page<AppUser> findAllUsers(Pageable pageable) {

        return userRepository.findAll(pageable);
    }

    @Override
    public UserProfile updateCurrentUserProfile(String email, UserProfileUpdateDto userProfileUpdateDto) {

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = user.getUserProfile();

        if (profile == null) {
            profile = new UserProfile();
            user.setUserProfile(profile);
        }

        userMapper.updateUserProfileFromDto(userProfileUpdateDto, profile);
        userRepository.save(user);

        return profile;
    }

    @Transactional
    @Override
    public AppUser updateUserRoles(Long userId, Set<String> roleNames, String requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);
        guardLastAdminOnRoleUpdate(user, roleNames);
        Set<UserRole> userRoles = roleNames.stream()
                .map(name -> userRoleRepository.findByName(name)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.ROLE_NOT_FOUND)))
                .collect(Collectors.toSet());
        user.setRoles(userRoles);
        userRepository.save(user);

        return user;
    }

    @Transactional
    @Override
    public AppUser deactivateUser(Long userId, String requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);

        guardLastAdmin(user);
        user.setEnabled(false);

        userRepository.save(user);
        redisRefreshTokenStore.revokeAllByUserEmail(user.getEmail());

        return user;
    }

    @Transactional
    @Override
    public AppUser activateUser(Long userId) {

        AppUser user = findUserById(userId);

        user.setEnabled(true);
        userRepository.save(user);

        return user;
    }

    @Transactional
    @Override
    public void deleteUser(Long userId, String requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);

        guardLastAdmin(user);
        String email = user.getEmail();

        userRepository.delete(user);
        redisRefreshTokenStore.revokeAllByUserEmail(email);
    }

    private void guardOwnAccount(Long targetUserId, String requesterEmail) {

        AppUser requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        if (requester.getId().equals(targetUserId)) {
            throw new DomainException(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
        }
    }

    private void guardLastAdmin(AppUser targetUser) {

        if (hasAdminRole(targetUser) && userRepository.countByRoles_Name(ROLE_ADMIN) <= 1) {
            throw new DomainException(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
        }
    }

    private void guardLastAdminOnRoleUpdate(AppUser targetUser, Set<String> newRoleNames) {

        boolean wasAdmin = hasAdminRole(targetUser);
        boolean willBeAdmin = newRoleNames.contains(ROLE_ADMIN);
        if (wasAdmin && !willBeAdmin && userRepository.countByRoles_Name(ROLE_ADMIN) <= 1) {
            throw new DomainException(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
        }
    }

    private boolean hasAdminRole(AppUser user) {

        return user.getRoles().stream()
                .anyMatch(role -> ROLE_ADMIN.equals(role.getName()));
    }
}
