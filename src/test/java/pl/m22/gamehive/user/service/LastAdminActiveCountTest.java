package pl.m22.gamehive.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.HashedPassword;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.common.exception.BaseException;
import pl.m22.gamehive.common.exception.ErrorCode;
import pl.m22.gamehive.support.SeededUsers;
import pl.m22.gamehive.user.model.AppUser;
import pl.m22.gamehive.user.model.UserRole;
import pl.m22.gamehive.user.repository.UserRepository;
import pl.m22.gamehive.user.repository.UserRoleRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GH-147: guard liczy AKTYWNYCH adminów. Przed poprawką licznik obejmował też wyłączonych, więc do systemu bez
 * działającego admina dało się dojść sekwencyjnie, bez żadnego wyścigu: A deaktywuje B (B dalej ma rolę → licznik 2),
 * potem A usuwa własne konto (licznik nadal 2) — zostaje wyłączony B, a reaktywacja wymaga admina.
 * Odwrotna strona: operacja na WYŁĄCZONYM adminie nie zmniejsza liczby aktywnych, więc guard jej nie blokuje.
 *
 * <p>{@code @Transactional} — rollback przywraca rolę Johna, którą każdy test odbiera, żeby X był jedynym aktywnym
 * adminem z fixture'ów.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LastAdminActiveCountTest {

    private static final Email ADMIN_X = new Email("gh147_seq_x@test.com");
    private static final Email ADMIN_Y = new Email("gh147_seq_y@test.com");
    private static final String PASSWORD = "secret123";

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @MockitoBean JavaMailSender mailSender;

    private UUID adminX;
    private UUID adminY;

    @BeforeEach
    void setUp() {
        AppUser john = userRepository.findById(SeededUsers.JOHN_ID).orElseThrow();
        john.replaceRoles(Set.of(role("ROLE_USER")));
        userRepository.save(john);

        adminX = createAdmin("gh147_seq_x", ADMIN_X);
        adminY = createAdmin("gh147_seq_y", ADMIN_Y);
    }

    @Test
    @DisplayName("X deaktywuje Y, potem X usuwa własne konto -> CANNOT_REMOVE_LAST_ADMIN (wyłączony Y się nie liczy)")
    void deactivateThenOwnDeletion_lastActiveAdminBlocked() {
        userService.deactivateUser(adminY, ADMIN_X);

        assertThatThrownBy(() -> userService.deleteOwnAccount(ADMIN_X, PASSWORD))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
        assertThat(userRepository.findById(adminX)).isPresent();
    }

    @Test
    @DisplayName("Jedyny aktywny admin usuwa WYŁĄCZONEGO admina -> przechodzi (liczba aktywnych się nie zmienia)")
    void deletingDisabledAdmin_allowed() {
        userService.deactivateUser(adminY, ADMIN_X);

        userService.deleteUser(adminY, ADMIN_X);

        assertThat(userRepository.findById(adminY)).isEmpty();
    }

    @Test
    @DisplayName("Jedyny aktywny admin odbiera ROLE_ADMIN WYŁĄCZONEMU adminowi -> przechodzi")
    void demotingDisabledAdmin_allowed() {
        userService.deactivateUser(adminY, ADMIN_X);

        AppUser demoted = userService.updateUserRoles(adminY, Set.of("ROLE_USER"), ADMIN_X);

        assertThat(demoted.getRoles()).extracting(UserRole::getName).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Po deaktywacji Y odebranie ROLE_ADMIN X (jedynemu aktywnemu) -> CANNOT_REMOVE_LAST_ADMIN")
    void demotingLastActiveAdmin_blocked() {
        userService.deactivateUser(adminY, ADMIN_X);

        // X jest teraz jedynym aktywnym adminem — wyłączony Y nie może zastąpić go w liczniku
        assertThatThrownBy(() -> userService.updateUserRoles(adminX, Set.of("ROLE_USER"), new Email("jane.smith@example.com")))
                .isInstanceOf(BaseException.class)
                .extracting(e -> ((BaseException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
    }

    private UUID createAdmin(String username, Email email) {
        AppUser user = AppUser.register(new Username(username), email, HashedPassword.fromHash("{noop}" + PASSWORD));
        user.activate();
        user.replaceRoles(new HashSet<>(Set.of(role("ROLE_ADMIN"), role("ROLE_USER"))));
        return userRepository.save(user).getId();
    }

    private UserRole role(String name) {
        return userRoleRepository.findByName(name).orElseThrow();
    }
}
