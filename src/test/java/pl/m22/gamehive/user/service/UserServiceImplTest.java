package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.dto.AddressDto;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.event.UserAuditEvent;
import pl.m22.gamehive.user.event.UserDeactivatedEvent;
import pl.m22.gamehive.user.event.UserDeletedEvent;
import pl.m22.gamehive.user.event.UserForceLoggedOutEvent;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.AuditAction;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class UserServiceImplTest {

    private static final Email JOHN_EMAIL = new Email("john.doe@example.com");
    private static final Email JANE_EMAIL = new Email("jane.smith@example.com");

    @Autowired UserService userService;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired ApplicationEvents applicationEvents;

    @BeforeEach
    void cleanRedis() {

        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("findUserByEmail() -> znaleziony")
    void findUserByEmail_found() {

        AppUser user = userService.findUserByEmail(JOHN_EMAIL);
        assertEquals("john_doe", user.getUsername().value());
    }

    @Test
    @DisplayName("findUserByEmail() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserByEmail_not_found() {

        Email email = new Email("nobody@test.com");

        assertThatThrownBy(() -> userService.findUserByEmail(email))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // --- findUserById ---

    @Test
    @DisplayName("findUserById() -> znaleziony")
    void findUserById_found() {

        AppUser user = userService.findUserById(SeededUsers.JOHN_ID);

        assertEquals("john_doe", user.getUsername().value());
        assertEquals("john.doe@example.com", user.getEmail().value());
    }

    @Test
    @DisplayName("findUserById() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserById_not_found() {

        assertThatThrownBy(() -> userService.findUserById(SeededUsers.UNKNOWN_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

    }

    // --- findUserByUsername ---

    @Test
    @DisplayName("findUserByUsername() -> znaleziony")
    void findUserByUsername_found() {

        AppUser user = userService.findUserByUsername(new Username("jane_smith"));

        assertEquals("jane.smith@example.com", user.getEmail().value());
    }

    @Test
    @DisplayName("findUserByUsername() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserByUsername_not_found() {

        Username username = new Username("ghost_user");

        assertThatThrownBy(() -> userService.findUserByUsername(username))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

    }

    // --- findAllUsers(Pageable) ---

    @Test
    @DisplayName("findAllUsers(Pageable) -> zwraca stronę z userami")
    void findAllUsers_pageable() {

        Page<AppUser> page = userService.findAllUsers(PageRequest.of(0, 10));

        assertTrue(page.getTotalElements() >= 2);
        assertTrue(page.getContent().stream()
                .anyMatch(u -> "john_doe".equals(u.getUsername().value())));
    }

    @Test
    @DisplayName("findAllUsers(Pageable) -> paginacja działa poprawnie")
    void findAllUsers_pageable_pagination() {

        Page<AppUser> page = userService.findAllUsers(PageRequest.of(0, 1));

        assertEquals(1, page.getContent().size());
        assertTrue(page.getTotalElements() >= 2);
    }

    // --- updateCurrentUserProfile ---

    @Test
    @Transactional
    @DisplayName("updateCurrentUserProfile() -> aktualizuje istniejący profil")
    void updateCurrentUserProfile_updates_existing_profile() {

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Johnny", null, null, null, null, null
        );

        UserProfile result = userService.updateCurrentUserProfile(JOHN_EMAIL, dto);

        assertEquals("Johnny", result.getFirstName());
        assertEquals("Doe", result.getLastName());
    }

    @Test
    @Transactional
    @DisplayName("updateCurrentUserProfile() -> tworzy nowy profil gdy brak")
    void updateCurrentUserProfile_creates_profile_when_null() {

        AppUser user = userService.findUserByEmail(JOHN_EMAIL);
        user.attachProfile(null);

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "New", "Profile", "+48111222333",
                new AddressDto("ul. Nowa 2", "Warszawa", "00-002", "Polska"),
                LocalDate.of(1990, Month.JANUARY, 1), "https://example.com/new.png"
        );

        UserProfile result = userService.updateCurrentUserProfile(JOHN_EMAIL, dto);

        assertNotNull(result);
        assertEquals("New", result.getFirstName());
        assertEquals("Profile", result.getLastName());
    }

    @Test
    @Transactional
    @DisplayName("updateCurrentUserProfile() -> null w DTO nie nadpisuje istniejących pól")
    void updateCurrentUserProfile_partialUpdate() {

        // seed: John Doe, 123 Main St, 123456789, 1990-05-15, https://example.com/johndoe.jpg
        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Johnny", null, null,
                new AddressDto(null, "Nowy Sącz", null, null),
                null, null
        );

        UserProfile result = userService.updateCurrentUserProfile(JOHN_EMAIL, dto);

        assertEquals("Johnny", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("123456789", result.getPhoneNumber().value());
        assertEquals("Nowy Sącz", result.getAddress().getCity());
        assertEquals(LocalDate.of(1990, Month.MAY, 15), result.getDateOfBirth());
        assertEquals("https://example.com/johndoe.jpg", result.getProfilePictureUrl().value());
    }

    @Test
    @DisplayName("updateCurrentUserProfile() -> nieistniejący email -> DomainException USER_NOT_FOUND")
    void updateCurrentUserProfile_user_not_found() {

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Test", null, null, null, null, null
        );
        Email email = new Email("nobody@test.com");

        assertThatThrownBy(() -> userService.updateCurrentUserProfile(email, dto))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // --- updateUserRoles ---

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> aktualizuje role usera (admin edytuje innego)")
    void updateUserRoles_updates() {

        userService.updateUserRoles(SeededUsers.JANE_ID, Set.of("ROLE_MODERATOR"), JOHN_EMAIL);

        AppUser user = userService.findUserById(SeededUsers.JANE_ID);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_MODERATOR"), names);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> zastępuje istniejące role nowym zestawem")
    void updateUserRoles_replaces() {

        userService.updateUserRoles(SeededUsers.JANE_ID, Set.of("ROLE_USER", "ROLE_MODERATOR"), JOHN_EMAIL);

        AppUser user = userService.findUserById(SeededUsers.JANE_ID);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_USER", "ROLE_MODERATOR"), names);
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void updateUserRoles_user_not_found() {

        Set<String> roles = Set.of("ROLE_USER");

        assertThatThrownBy(() -> userService.updateUserRoles(SeededUsers.UNKNOWN_ID, roles, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejąca rola -> DomainException ROLE_NOT_FOUND")
    void updateUserRoles_role_not_found() {

        Set<String> roles = Set.of("ROLE_GHOST");

        assertThatThrownBy(() -> userService.updateUserRoles(SeededUsers.JANE_ID, roles, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    @DisplayName("updateUserRoles() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void updateUserRoles_self_blocked() {

        Set<String> roles = Set.of("ROLE_USER");

        assertThatThrownBy(() -> userService.updateUserRoles(SeededUsers.JOHN_ID, roles, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("updateUserRoles() -> odbiera ROLE_ADMIN ostatniemu adminowi -> CANNOT_REMOVE_LAST_ADMIN")
    void updateUserRoles_last_admin_demote_blocked() {

        Set<String> roles = Set.of("ROLE_USER");

        assertThatThrownBy(() -> userService.updateUserRoles(SeededUsers.JOHN_ID, roles, JANE_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> demote admina gdy są inni admini -> OK")
    void updateUserRoles_demote_admin_when_others_exist_ok() {

        userService.updateUserRoles(SeededUsers.JANE_ID, Set.of("ROLE_USER", "ROLE_ADMIN"), JOHN_EMAIL);

        userService.updateUserRoles(SeededUsers.JOHN_ID, Set.of("ROLE_USER"), JANE_EMAIL);

        AppUser john = userService.findUserById(SeededUsers.JOHN_ID);
        Set<String> names = john.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_USER"), names);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> publikuje UserAuditEvent ROLE_CHANGE z actor + before/after")
    void updateUserRoles_publishesAuditEvent() {

        userService.updateUserRoles(SeededUsers.JANE_ID, Set.of("ROLE_MODERATOR"), JOHN_EMAIL);

        UserAuditEvent event = applicationEvents.stream(UserAuditEvent.class)
                .filter(e -> e.targetId().equals(SeededUsers.JANE_ID))
                .findFirst().orElseThrow();
        assertEquals(AuditAction.ROLE_CHANGE, event.action());
        assertEquals("jane.smith@example.com", event.targetEmail());
        assertEquals("john.doe@example.com", event.actor());
        assertTrue(event.details().contains("ROLE_USER"));       // before
        assertTrue(event.details().contains("ROLE_MODERATOR"));  // after
    }

    // --- deactivateUser ---

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> ustawia enabled=false")
    void deactivateUser_disables() {

        userService.deactivateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        AppUser user = userService.findUserById(SeededUsers.JANE_ID);
        assertFalse(user.isEnabled());
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> publikuje UserDeactivatedEvent (rewokacja Redis dzieje się AFTER_COMMIT)")
    void deactivateUser_publishes_event() {

        userService.deactivateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserDeactivatedEvent.class)
                .filter(e -> e.email().equals("jane.smith@example.com"))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("deactivateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deactivateUser_user_not_found() {

        assertThatThrownBy(() -> userService.deactivateUser(SeededUsers.UNKNOWN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("deactivateUser() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void deactivateUser_self_blocked() {

        assertThatThrownBy(() -> userService.deactivateUser(SeededUsers.JOHN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("deactivateUser() -> ostatni admin -> CANNOT_REMOVE_LAST_ADMIN")
    void deactivateUser_last_admin_blocked() {

        assertThatThrownBy(() -> userService.deactivateUser(SeededUsers.JOHN_ID, JANE_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> dezaktywacja admina gdy są inni admini -> OK")
    void deactivateUser_admin_when_others_exist_ok() {

        userService.updateUserRoles(SeededUsers.JANE_ID, Set.of("ROLE_USER", "ROLE_ADMIN"), JOHN_EMAIL);

        userService.deactivateUser(SeededUsers.JOHN_ID, JANE_EMAIL);

        AppUser john = userService.findUserById(SeededUsers.JOHN_ID);
        assertFalse(john.isEnabled());
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> publikuje UserAuditEvent DEACTIVATE z actor")
    void deactivateUser_publishesAuditEvent() {

        userService.deactivateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserAuditEvent.class)
                .filter(e -> e.targetId().equals(SeededUsers.JANE_ID)
                        && e.action() == AuditAction.DEACTIVATE
                        && e.actor().equals("john.doe@example.com"))
                .count();
        assertEquals(1, count);
    }

    // --- activateUser ---

    @Test
    @Transactional
    @DisplayName("activateUser() -> ustawia enabled=true dla zdezaktywowanego usera")
    void activateUser_enables() {

        userService.deactivateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        userService.activateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        AppUser user = userService.findUserById(SeededUsers.JANE_ID);
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("activateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void activateUser_user_not_found() {

        assertThatThrownBy(() -> userService.activateUser(SeededUsers.UNKNOWN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @Transactional
    @DisplayName("activateUser() -> publikuje UserAuditEvent ACTIVATE z actor")
    void activateUser_publishesAuditEvent() {

        userService.deactivateUser(SeededUsers.JANE_ID, JOHN_EMAIL);
        userService.activateUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserAuditEvent.class)
                .filter(e -> e.targetId().equals(SeededUsers.JANE_ID)
                        && e.action() == AuditAction.ACTIVATE
                        && e.actor().equals("john.doe@example.com"))
                .count();
        assertEquals(1, count);
    }

    // --- deleteUser ---

    @Test
    @Transactional
    @DisplayName("deleteUser() -> usuwa usera z bazy")
    void deleteUser_removes_user() {

        userService.deleteUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        assertThatThrownBy(() -> userService.findUserById(SeededUsers.JANE_ID))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @Transactional
    @DisplayName("deleteUser() -> publikuje UserDeletedEvent (rewokacja Redis dzieje się AFTER_COMMIT)")
    void deleteUser_publishes_event() {

        userService.deleteUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserDeletedEvent.class)
                .filter(e -> e.email().equals("jane.smith@example.com"))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("deleteUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deleteUser_user_not_found() {

        assertThatThrownBy(() -> userService.deleteUser(SeededUsers.UNKNOWN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("deleteUser() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void deleteUser_self_blocked() {

        assertThatThrownBy(() -> userService.deleteUser(SeededUsers.JOHN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("deleteUser() -> ostatni admin -> CANNOT_REMOVE_LAST_ADMIN")
    void deleteUser_last_admin_blocked() {

        assertThatThrownBy(() -> userService.deleteUser(SeededUsers.JOHN_ID, JANE_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("deleteUser() -> publikuje UserAuditEvent DELETE (target zachowany mimo usunięcia)")
    void deleteUser_publishesAuditEvent() {

        userService.deleteUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        UserAuditEvent event = applicationEvents.stream(UserAuditEvent.class)
                .filter(e -> e.action() == AuditAction.DELETE)
                .findFirst().orElseThrow();
        assertEquals(SeededUsers.JANE_ID, event.targetId());
        assertEquals("jane.smith@example.com", event.targetEmail());
        assertEquals("john.doe@example.com", event.actor());
    }

    // --- forceLogout ---

    @Test
    @Transactional
    @DisplayName("forceLogout() -> publikuje UserForceLoggedOutEvent (rewokacja Redis dzieje się AFTER_COMMIT)")
    void forceLogout_publishes_event() {

        userService.forceLogoutUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserForceLoggedOutEvent.class)
                .filter(e -> e.email().equals("jane.smith@example.com"))
                .count();
        assertEquals(1, count);
    }

    @Test
    @Transactional
    @DisplayName("forceLogout() -> nie zmienia stanu konta (enabled pozostaje true)")
    void forceLogout_does_not_change_account_state() {

        userService.forceLogoutUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        AppUser user = userService.findUserById(SeededUsers.JANE_ID);
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("forceLogout() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void forceLogout_self_blocked() {

        assertThatThrownBy(() -> userService.forceLogoutUser(SeededUsers.JOHN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("forceLogout() -> nieistniejący user -> ApplicationException USER_NOT_FOUND")
    void forceLogout_user_not_found() {

        assertThatThrownBy(() -> userService.forceLogoutUser(SeededUsers.UNKNOWN_ID, JOHN_EMAIL))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @Transactional
    @DisplayName("forceLogout() -> publikuje UserAuditEvent FORCE_LOGOUT z actor")
    void forceLogout_publishesAuditEvent() {

        userService.forceLogoutUser(SeededUsers.JANE_ID, JOHN_EMAIL);

        long count = applicationEvents.stream(UserAuditEvent.class)
                .filter(e -> e.targetId().equals(SeededUsers.JANE_ID)
                        && e.action() == AuditAction.FORCE_LOGOUT
                        && e.actor().equals("john.doe@example.com"))
                .count();
        assertEquals(1, count);
    }

}
