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
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.auth.jwt.service.RedisRefreshTokenStore;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;
import pl.m22.gamehive.user.model.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceImplTest {

    @Autowired UserService userService;
    @Autowired RedisRefreshTokenStore redisRefreshTokenStore;
    @Autowired RedisTemplate<String, String> redisTemplate;

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
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.findUserByEmail("nobody@test.com"));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
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
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.findUserById(999L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
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
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.findUserByUsername("ghost_user"));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
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
        user.setUserProfile(null);

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

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.updateCurrentUserProfile("nobody@test.com", dto));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    // --- updateUserRoles ---

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> aktualizuje role usera")
    void updateUserRoles_updates() {
        userService.updateUserRoles(1L, Set.of("ROLE_USER"));

        AppUser user = userService.findUserById(1L);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_USER"), names);
    }

    @Test
    @Transactional
    @DisplayName("updateUserRoles() -> zastępuje istniejące role nowym zestawem")
    void updateUserRoles_replaces() {
        userService.updateUserRoles(2L, Set.of("ROLE_MODERATOR"));

        AppUser user = userService.findUserById(2L);
        Set<String> names = user.getRoles().stream()
                .map(UserRole::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ROLE_MODERATOR"), names);
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void updateUserRoles_user_not_found() {
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.updateUserRoles(999L, Set.of("ROLE_USER")));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateUserRoles() -> nieistniejąca rola -> DomainException ROLE_NOT_FOUND")
    void updateUserRoles_role_not_found() {
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.updateUserRoles(1L, Set.of("ROLE_GHOST")));

        assertEquals(ErrorCode.ROLE_NOT_FOUND, ex.getErrorCode());
    }

    // --- deactivateUser ---

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> ustawia enabled=false")
    void deactivateUser_disables() {
        userService.deactivateUser(1L);

        AppUser user = userService.findUserById(1L);
        assertFalse(user.isEnabled());
    }

    @Test
    @Transactional
    @DisplayName("deactivateUser() -> usuwa refresh tokeny usera z Redisa")
    void deactivateUser_revokes_refresh_tokens() {
        String jti = "test-jti-deactivate";
        redisRefreshTokenStore.saveRefreshToken(jti, "john.doe@example.com", Instant.now().plusSeconds(3600));
        assertTrue(redisRefreshTokenStore.existsByJti(jti));

        userService.deactivateUser(1L);

        assertFalse(redisRefreshTokenStore.existsByJti(jti));
    }

    @Test
    @DisplayName("deactivateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deactivateUser_user_not_found() {
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.deactivateUser(999L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    // --- activateUser ---

    @Test
    @Transactional
    @DisplayName("activateUser() -> ustawia enabled=true dla zdezaktywowanego usera")
    void activateUser_enables() {
        userService.deactivateUser(1L);

        userService.activateUser(1L);

        AppUser user = userService.findUserById(1L);
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("activateUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void activateUser_user_not_found() {
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.activateUser(999L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    // --- deleteUser ---

    @Test
    @Transactional
    @DisplayName("deleteUser() -> usuwa usera z bazy")
    void deleteUser_removes_user() {
        userService.deleteUser(2L);

        DomainException ex = assertThrows(DomainException.class,
                () -> userService.findUserById(2L));
        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @Transactional
    @DisplayName("deleteUser() -> usuwa refresh tokeny usera z Redisa")
    void deleteUser_revokes_refresh_tokens() {
        String jti = "test-jti-delete";
        redisRefreshTokenStore.saveRefreshToken(jti, "jane.smith@example.com", Instant.now().plusSeconds(3600));
        assertTrue(redisRefreshTokenStore.existsByJti(jti));

        userService.deleteUser(2L);

        assertFalse(redisRefreshTokenStore.existsByJti(jti));
    }

    @Test
    @DisplayName("deleteUser() -> nieistniejący user -> DomainException USER_NOT_FOUND")
    void deleteUser_user_not_found() {
        DomainException ex = assertThrows(DomainException.class,
                () -> userService.deleteUser(999L));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
    }
}