package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceImplTest {

    @Autowired UserService userService;

    @Test
    @DisplayName("findAllUsers() -> zawiera seed userów")
    void findAllUsers_contains_seed_users() {
        List<AppUser> users = userService.findAllUsers();
        assertFalse(users.isEmpty());
        assertTrue(users.stream().anyMatch(u -> "john.doe@example.com".equals(u.getEmail())));
        assertTrue(users.stream().anyMatch(u -> "jane.smith@example.com".equals(u.getEmail())));
    }

    @Test
    @DisplayName("findUserByEmail() -> znaleziony")
    void findUserByEmail_found() {
        Optional<AppUser> user = userService.findUserByEmail("john.doe@example.com");
        assertTrue(user.isPresent());
        assertEquals("john_doe", user.get().getUsername());
    }

    @Test
    @DisplayName("findUserByEmail() -> nieznaleziony")
    void findUserByEmail_not_found() {
        assertFalse(userService.findUserByEmail("nobody@test.com").isPresent());
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
}