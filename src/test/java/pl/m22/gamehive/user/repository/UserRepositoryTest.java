package pl.m22.gamehive.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.user.model.AppUser;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("findByEmail -> zwraca usera dla istniejącego maila")
    void findByEmail_ok() {
        Optional<AppUser> u = userRepository.findByEmail("john.doe@example.com");
        assertThat(u).isPresent();
        assertThat(u.get().getUsername()).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("existsByUsername -> true/false")
    void existsByUsername() {
        assertThat(userRepository.existsByUsername("john_doe")).isTrue();
        assertThat(userRepository.existsByUsername("nope")).isFalse();
    }

    @Test
    @DisplayName("findAllUsersByRoles_Name('ROLE_ADMIN') -> zwraca adminów")
    void findAllByRole() {
        List<AppUser> admins = userRepository.findAllUsersByRoles_Name("ROLE_ADMIN");
        assertThat(admins).extracting(AppUser::getEmail).contains("john.doe@example.com");
        assertThat(admins).extracting(AppUser::getEmail).doesNotContain("jane.smith@example.com");
    }
}
