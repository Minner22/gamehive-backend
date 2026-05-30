package pl.m22.gamehive.common.domain;

import org.junit.jupiter.api.Test;
import pl.m22.gamehive.common.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.INVALID_USERNAME;

class UsernameTest {

    @Test
    void creates_username_for_valid_value() {
        assertThat(new Username("john_doe").value()).isEqualTo("john_doe");
    }

    @Test
    void accepts_dot_underscore_and_hyphen() {
        assertThat(new Username("a.b_c-d").value()).isEqualTo("a.b_c-d");
    }

    @Test
    void accepts_min_and_max_length_boundaries() {
        assertThat(new Username("abc").value()).isEqualTo("abc");          // 3 znaki
        assertThat(new Username("a".repeat(30)).value()).hasSize(30);      // 30 znaków
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new Username(null))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new Username("  "))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void rejects_too_short() {
        assertThatThrownBy(() -> new Username("ab"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void rejects_too_long() {
        assertThatThrownBy(() -> new Username("a".repeat(31)))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void rejects_space() {
        assertThatThrownBy(() -> new Username("john doe"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void rejects_illegal_special_character() {
        assertThatThrownBy(() -> new Username("john!"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_USERNAME);
    }

    @Test
    void equal_values_are_equal() {
        assertThat(new Username("john_doe")).isEqualTo(new Username("john_doe"));
    }

    @Test
    void equal_values_share_hashcode() {
        assertThat(new Username("john_doe")).hasSameHashCodeAs(new Username("john_doe"));
    }
}
