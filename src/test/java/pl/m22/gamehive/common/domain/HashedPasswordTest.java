package pl.m22.gamehive.common.domain;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.m22.gamehive.common.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.INVALID_HASHED_PASSWORD;

class HashedPasswordTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void fromRaw_then_matches_returns_true() {
        HashedPassword hp = HashedPassword.fromRaw("password123", encoder);
        assertThat(hp.matches("password123", encoder)).isTrue();
    }

    @Test
    void fromRaw_matches_wrong_password_returns_false() {
        HashedPassword hp = HashedPassword.fromRaw("password123", encoder);
        assertThat(hp.matches("wrong", encoder)).isFalse();
    }

    @Test
    void fromRaw_rejects_blank() {
        assertThatThrownBy(() -> HashedPassword.fromRaw("  ", encoder))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_HASHED_PASSWORD);
    }

    @Test
    void toString_does_not_expose_hash() {
        HashedPassword hp = HashedPassword.fromRaw("password123", encoder);
        assertThat(hp).hasToString("HashedPassword[***]");
        assertThat(hp.toString()).doesNotContain(hp.value());
    }

    @Test
    void fromHash_accepts_value_with_encoder_prefix() {
        assertThat(HashedPassword.fromHash("{bcrypt}$2a$10$abcdefghijklmnopqrstuv").value())
                .startsWith("{bcrypt}");
    }

    @Test
    void fromHash_rejects_value_without_prefix() {
        assertThatThrownBy(() -> HashedPassword.fromHash("plaintext"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_HASHED_PASSWORD);
    }

    @Test
    void fromHash_rejects_null() {
        assertThatThrownBy(() -> HashedPassword.fromHash(null))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_HASHED_PASSWORD);
    }

    @Test
    void equal_values_are_equal() {
        assertThat(HashedPassword.fromHash("{noop}abc"))
                .isEqualTo(HashedPassword.fromHash("{noop}abc"))
                .hasSameHashCodeAs(HashedPassword.fromHash("{noop}abc"));
    }

    @Test
    void different_values_are_not_equal() {
        assertThat(HashedPassword.fromHash("{noop}abc"))
                .isNotEqualTo(HashedPassword.fromHash("{noop}xyz"));
    }
}