package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.user.dto.UserProfileUpdateDto;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserProfile;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceImplTest {

    @Autowired UserService userService;

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
}