package pl.m22.gamehive.user.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.ApplicationException;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.common.logging.CorrelationIdFilter;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.event.*;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AppUser findUserByEmail(Email email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserById(UUID id) {

        return userRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public AppUser findUserByUsername(Username username) {

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Page<AppUser> findAllUsers(Pageable pageable) {

        return userRepository.findAll(pageable);
    }

    @Override
    public UserProfile updateCurrentUserProfile(Email email, UserProfileUpdateDto userProfileUpdateDto) {

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = user.getUserProfile();

        if (profile == null) {
            profile = new UserProfile();
            user.attachProfile(profile);
        }

        profile.updateFrom(userProfileUpdateDto);

        userRepository.save(user);

        return profile;
    }

    @Transactional
    @Override
    public AppUser updateUserRoles(UUID userId, Set<String> roleNames, Email requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);
        guardLastAdminOnRoleUpdate(user, roleNames);

        Set<String> rolesBefore = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());

        Set<UserRole> userRoles = roleNames.stream()
                .map(name -> userRoleRepository.findByName(name)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.ROLE_NOT_FOUND)))
                .collect(Collectors.toSet());

        user.replaceRoles(userRoles);

        userRepository.save(user);
        eventPublisher.publishEvent(new UserRolesUpdatedEvent(user.getEmail().value()));
        publishAudit(AuditAction.ROLE_CHANGE, user.getId(), user.getEmail().value(), requesterEmail, rolesDiffJson(rolesBefore, roleNames));

        return user;
    }

    @Transactional
    @Override
    public AppUser deactivateUser(UUID userId, Email requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);

        guardLastAdmin(user);

        user.deactivate();

        userRepository.save(user);
        eventPublisher.publishEvent(new UserDeactivatedEvent(user.getEmail().value()));
        publishAudit(AuditAction.DEACTIVATE, user.getId(), user.getEmail().value(), requesterEmail, null);

        return user;
    }

    @Transactional
    @Override
    public AppUser activateUser(UUID userId, Email requesterEmail) {

        AppUser user = findUserById(userId);

        user.activate();

        userRepository.save(user);
        eventPublisher.publishEvent(new UserReactivatedEvent(user.getEmail().value()));
        publishAudit(AuditAction.ACTIVATE, user.getId(), user.getEmail().value(), requesterEmail, null);

        return user;
    }

    @Transactional
    @Override
    public void deleteUser(UUID userId, Email requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);

        guardLastAdmin(user);
        String email = user.getEmail().value();
        UUID targetId = user.getId();

        userRepository.delete(user);
        eventPublisher.publishEvent(new UserDeletedEvent(email));
        publishAudit(AuditAction.DELETE, targetId, email, requesterEmail, null);
    }

    @Override
    public void deleteOwnAccount(Email requesterEmail, String rawPassword) {

        AppUser user = findUserByEmail(requesterEmail);

        if (!user.getPassword().matches(rawPassword, passwordEncoder)) {
            throw new DomainException(ErrorCode.INVALID_PASSWORD);
        }

        guardLastAdmin(user);

        String email = user.getEmail().value();
        UUID targetId = user.getId();

        userRepository.delete(user);

        eventPublisher.publishEvent(new UserDeletedEvent(email));
        publishAudit(AuditAction.DELETE, targetId, email, requesterEmail, null);
    }

    @Transactional(readOnly = true)
    @Override
    public void forceLogoutUser(UUID userId, Email requesterEmail) {

        guardOwnAccount(userId, requesterEmail);
        AppUser user = findUserById(userId);

        eventPublisher.publishEvent(new UserForceLoggedOutEvent(user.getEmail().value()));
        publishAudit(AuditAction.FORCE_LOGOUT, user.getId(), user.getEmail().value(), requesterEmail, null);
    }

    private void guardOwnAccount(UUID targetUserId, Email requesterEmail) {

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

    private void publishAudit(AuditAction action, UUID targetId, String targetEmail, Email requester, String details) {

        eventPublisher.publishEvent(new UserAuditEvent(action, targetId, targetEmail, requester.value(), details, currentCorrelationId()));
    }

    private String currentCorrelationId() {

        return MDC.get(CorrelationIdFilter.CORRELATION_ID);
    }

    // Role to kontrolowany słownik (ROLE_*), bez znaków wymagających escapowania -> bezpieczny ręczny JSON.
    private static String rolesDiffJson(Set<String> before, Set<String> after) {

        return "{\"before\":" + toJsonArray(before) + ", \"after\":" + toJsonArray(after) + "}";
    }

    private static String toJsonArray(Set<String> roles) {

        return roles.stream()
                .sorted()
                .map(role -> "\"" + role + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
