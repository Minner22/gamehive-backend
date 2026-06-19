package pl.m22.gamehive.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.common.domain.Username;
import pl.m22.gamehive.user.model.AppUser;

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
        assertThat(u.get().getUsername().value()).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("existsByUsername -> true/false")
    void existsByUsername() {
        assertThat(userRepository.existsByUsername(new Username("john_doe"))).isTrue();
        assertThat(userRepository.existsByUsername(new Username("nope"))).isFalse();
    }
}
