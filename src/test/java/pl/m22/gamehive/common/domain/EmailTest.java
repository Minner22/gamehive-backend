package pl.m22.gamehive.common.domain;

import org.junit.jupiter.api.Test;
import pl.m22.gamehive.common.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.INVALID_EMAIL_FORMAT;

class EmailTest {

    @Test
    void creates_email_for_valid_value() {
        assertThat(new Email("john.doe@example.com").value()).isEqualTo("john.doe@example.com");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_EMAIL_FORMAT);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_EMAIL_FORMAT);
    }

    @Test
    void rejects_missing_at_sign() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_EMAIL_FORMAT);
    }

    @Test
    void rejects_missing_domain() {
        assertThatThrownBy(() -> new Email("john@"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_EMAIL_FORMAT);
    }

    @Test
    void equal_values_are_equal() {
        assertThat(new Email("a@b.com")).isEqualTo(new Email("a@b.com"));
    }

    @Test
    void equal_values_share_hashcode() {
        assertThat(new Email("a@b.com")).hasSameHashCodeAs(new Email("a@b.com"));
    }

    @Test
    void different_values_are_not_equal() {
        assertThat(new Email("a@b.com")).isNotEqualTo(new Email("c@d.com"));
    }

    @Test
    void obfuscated_masks_local_part() {
        assertThat(new Email("john.doe@example.com").obfuscated())
                .isEqualTo("jo******@example.com");
    }

    @Test
    void obfuscated_leaves_short_local_part_untouched() {
        assertThat(new Email("a@b.com").obfuscated()).isEqualTo("a@b.com");
    }

    @Test
    void toString_returns_raw_value() {
        assertThat(new Email("a@b.com")).hasToString("a@b.com");
    }
}