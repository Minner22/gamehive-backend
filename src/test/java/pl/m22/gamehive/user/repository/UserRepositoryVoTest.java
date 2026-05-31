package pl.m22.gamehive.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.m22.gamehive.common.domain.Email;
import pl.m22.gamehive.common.domain.Username;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryVoTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmail_vo_delegates_to_string_query() {
        assertThat(userRepository.findByEmail(new Email("john.doe@example.com")))
                .isPresent()
                .get()
                .satisfies(u -> assertThat(u.getUsername().value()).isEqualTo("john_doe"));
    }

    @Test
    void findByUsername_vo_delegates_to_string_query() {
        assertThat(userRepository.findByUsername(new Username("john_doe"))).isPresent();
    }

    @Test
    void existsByEmail_vo_returns_true_for_seeded_user() {
        assertThat(userRepository.existsByEmail(new Email("john.doe@example.com"))).isTrue();
    }

    @Test
    void existsByEmail_vo_returns_false_for_unknown_user() {
        assertThat(userRepository.existsByEmail(new Email("nobody@example.com"))).isFalse();
    }
}
