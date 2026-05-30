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
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.event.UserDeactivatedEvent;
import pl.m22.gamehive.user.event.UserDeletedEvent;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class UserServiceImplTest {

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

        AppUser user = userService.findUserByEmail("john.doe@example.com");
        assertEquals("john_doe", user.getUsername());
    }

    @Test
    @DisplayName("findUserByEmail() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserByEmail_not_found() {

        assertThatThrownBy(() -> userService.findUserByEmail("nobody@test.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("emailExists() -> true dla istniejącego emaila")
    void emailExists_true() {
        assertTrue(userService.emailExists("jane.smith@example.com"));
    }

    @Test
    @DisplayName("emailExists() -> false dla nieistniejącego emaila")
    void emailExists_false() {
        assertFalse(userService.emailExists("ghost@test.com"));
    }

    @Test
    @DisplayName("usernameExists() -> true dla istniejącej nazwy")
    void usernameExists_true() {
        assertTrue(userService.usernameExists("john_doe"));
    }

    @Test
    @DisplayName("usernameExists() -> false dla nieistniejącej nazwy")
    void usernameExists_false() {
        assertFalse(userService.usernameExists("ghost_user"));
    }

    @Test
    @DisplayName("findAllUserEmails() -> zwraca emaile userów z rolą ROLE_USER")
    void findAllUserEmails_returns_role_user_emails() {

        List<String> emails = userService.findAllUserEmails();
        assertTrue(emails.contains("john.doe@example.com"));
        assertTrue(emails.contains("jane.smith@example.com"));
    }

    @Test
    @DisplayName("findCredentialsByEmail() -> znaleziony")
    void findCredentialsByEmail_found() {

        assertTrue(userService.findCredentialsByEmail("john.doe@example.com").isPresent());
    }

    @Test
    @DisplayName("findCredentialsByEmail() -> nieznaleziony")
    void findCredentialsByEmail_not_found() {

        assertFalse(userService.findCredentialsByEmail("nobody@test.com").isPresent());
    }

    // --- findUserById ---

    @Test
    @DisplayName("findUserById() -> znaleziony")
    void findUserById_found() {

        AppUser user = userService.findUserById(1L);

        assertEquals("john_doe", user.getUsername());
        assertEquals("john.doe@example.com", user.getEmail());
    }

    @Test
    @DisplayName("findUserById() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserById_not_found() {

        assertThatThrownBy(() -> userService.findUserById(999L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

    }

    // --- findUserByUsername ---

    @Test
    @DisplayName("findUserByUsername() -> znaleziony")
    void findUserByUsername_found() {

        AppUser user = userService.findUserByUsername("jane_smith");

        assertEquals("jane.smith@example.com", user.getEmail());
    }

    @Test
    @DisplayName("findUserByUsername() -> nieznaleziony -> DomainException USER_NOT_FOUND")
    void findUserByUsername_not_found() {

        assertThatThrownBy(() -> userService.findUserByUsername("ghost_user"))
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
                .anyMatch(u -> "john_doe".equals(u.getUsername())));
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

        UserProfile result = userService.updateCurrentUserProfile("john.doe@example.com", dto);

        assertEquals("Johnny", result.getFirstName());
        assertEquals("Doe", result.getLastName());
    }

    @Test
    @Transactional
    @DisplayName("updateCurrentUserProfile() -> tworzy nowy profil gdy brak")
    void updateCurrentUserProfile_creates_profile_when_null() {

        AppUser user = userService.findUserByEmail("john.doe@example.com");
        user.attachProfile(null);

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "New", "Profile", "+48111222333", "Warszawa",
                LocalDate.of(1990, 1, 1), "https://example.com/new.png"
        );

        UserProfile result = userService.updateCurrentUserProfile("john.doe@example.com", dto);

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
                "Johnny", null, null, "Nowy adres", null, null
        );

        UserProfile result = userService.updateCurrentUserProfile("john.doe@example.com", dto);

        assertEquals("Johnny", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals("123456789", result.getPhoneNumber());
        assertEquals("Nowy adres", result.getAddress());
        assertEquals(LocalDate.of(1990, 5, 15), result.getDateOfBirth());
        assertEquals("https://example.com/johndoe.jpg", result.getProfilePictureUrl());
    }

    @Test
    @DisplayName("updateCurrentUserProfile() -> nieistniejący email -> DomainException USER_NOT_FOUND")
    void updateCurrentUserProfile_user_not_found() {

        UserProfileUpdateDto dto = new UserProfileUpdateDto(
                "Test", null, null, null, null, null
        );

        assertThatThrownBy(() -> userService.updateCurrentUserProfile("nobody@test.com", dto))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // --- updateUserRoles ---

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> aktualizuje role usera (admin edytuje innego)")
    void updateUserRoles_updates() {

        userService.updateUserRoles(2L, Set.of("ROLE_MODERATOR"), "john.doe@example.com");

        AppUser user = userService.findUserById(2L);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_MODERATOR"), names);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> zastępuje istniejące role nowym zestawem")
    void updateUserRoles_replaces() {

        userService.updateUserRoles(2L, Set.of("ROLE_USER", "ROLE_MODERATOR"), "john.doe@example.com");

        AppUser user = userService.findUserById(2L);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_USER", "ROLE_MODERATOR"), names);
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void updateUserRoles_user_not_found() {

        assertThatThrownBy(() -> userService.updateUserRoles(999L, Set.of("ROLE_USER"), "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejąca rola -> DomainException ROLE_NOT_FOUND")
    void updateUserRoles_role_not_found() {

        assertThatThrownBy(() ->userService.updateUserRoles(2L, Set.of("ROLE_GHOST"), "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    @DisplayName("updateUserRoles() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void updateUserRoles_self_blocked() {

        assertThatThrownBy(() -> userService.updateUserRoles(1L, Set.of("ROLE_USER"), "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("updateUserRoles() -> odbiera ROLE_ADMIN ostatniemu adminowi -> CANNOT_REMOVE_LAST_ADMIN")
    void updateUserRoles_last_admin_demote_blocked() {

        assertThatThrownBy(() ->userService.updateUserRoles(1L, Set.of("ROLE_USER"), "jane.smith@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> demote admina gdy są inni admini -> OK")
    void updateUserRoles_demote_admin_when_others_exist_ok() {

        userService.updateUserRoles(2L, Set.of("ROLE_USER", "ROLE_ADMIN"), "john.doe@example.com");

        userService.updateUserRoles(1L, Set.of("ROLE_USER"), "jane.smith@example.com");

        AppUser john = userService.findUserById(1L);
        Set<String> names = john.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_USER"), names);
    }

    // --- deactivateUser ---

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> ustawia enabled=false")
    void deactivateUser_disables() {

        userService.deactivateUser(2L, "john.doe@example.com");

        AppUser user = userService.findUserById(2L);
        assertFalse(user.isEnabled());
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> publikuje UserDeactivatedEvent (rewokacja Redis dzieje się AFTER_COMMIT)")
    void deactivateUser_publishes_event() {

        userService.deactivateUser(2L, "john.doe@example.com");

        long count = applicationEvents.stream(UserDeactivatedEvent.class)
                .filter(e -> e.email().equals("jane.smith@example.com"))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("deactivateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deactivateUser_user_not_found() {

        assertThatThrownBy(() -> userService.deactivateUser(999L, "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("deactivateUser() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void deactivateUser_self_blocked() {

        assertThatThrownBy(() -> userService.deactivateUser(1L, "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("deactivateUser() -> ostatni admin -> CANNOT_REMOVE_LAST_ADMIN")
    void deactivateUser_last_admin_blocked() {

        assertThatThrownBy(() -> userService.deactivateUser(1L, "jane.smith@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> dezaktywacja admina gdy są inni admini -> OK")
    void deactivateUser_admin_when_others_exist_ok() {

        userService.updateUserRoles(2L, Set.of("ROLE_USER", "ROLE_ADMIN"), "john.doe@example.com");

        userService.deactivateUser(1L, "jane.smith@example.com");

        AppUser john = userService.findUserById(1L);
        assertFalse(john.isEnabled());
    }

    // --- activateUser ---

    @Test
    @Transactional
    @DisplayName("activateUser() -> ustawia enabled=true dla zdezaktywowanego usera")
    void activateUser_enables() {

        userService.deactivateUser(2L, "john.doe@example.com");

        userService.activateUser(2L);

        AppUser user = userService.findUserById(2L);
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("activateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void activateUser_user_not_found() {

        assertThatThrownBy(() -> userService.activateUser(999L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // --- deleteUser ---

    @Test
    @Transactional
    @DisplayName("deleteUser() -> usuwa usera z bazy")
    void deleteUser_removes_user() {

        userService.deleteUser(2L, "john.doe@example.com");

        assertThatThrownBy(() -> userService.findUserById(2L))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @Transactional
    @DisplayName("deleteUser() -> publikuje UserDeletedEvent (rewokacja Redis dzieje się AFTER_COMMIT)")
    void deleteUser_publishes_event() {

        userService.deleteUser(2L, "john.doe@example.com");

        long count = applicationEvents.stream(UserDeletedEvent.class)
                .filter(e -> e.email().equals("jane.smith@example.com"))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("deleteUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deleteUser_user_not_found() {

        assertThatThrownBy(() -> userService.deleteUser(999L, "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("deleteUser() -> self -> DomainException CANNOT_MODIFY_OWN_ACCOUNT")
    void deleteUser_self_blocked() {

        assertThatThrownBy(() -> userService.deleteUser(1L, "john.doe@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_MODIFY_OWN_ACCOUNT);
    }

    @Test
    @DisplayName("deleteUser() -> ostatni admin -> CANNOT_REMOVE_LAST_ADMIN")
    void deleteUser_last_admin_blocked() {

        assertThatThrownBy(() -> userService.deleteUser(1L, "jane.smith@example.com"))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }
}