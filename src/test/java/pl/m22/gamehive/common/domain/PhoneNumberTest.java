package pl.m22.gamehive.common.domain;

import org.junit.jupiter.api.Test;
import pl.m22.gamehive.common.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.m22.gamehive.common.exception.ErrorCode.INVALID_PHONE_NUMBER;

class PhoneNumberTest {

    @Test
    void accepts_e164_with_plus() {
        assertThat(new PhoneNumber("+48123456789").value()).isEqualTo("+48123456789");
    }

    @Test
    void accepts_digits_without_plus() {
        assertThat(new PhoneNumber("123456789").value()).isEqualTo("123456789");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new PhoneNumber(null))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new PhoneNumber("  "))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void rejects_letters() {
        assertThatThrownBy(() -> new PhoneNumber("haha"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void rejects_leading_zero() {
        assertThatThrownBy(() -> new PhoneNumber("0123456789"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void rejects_too_long() {
        assertThatThrownBy(() -> new PhoneNumber("+1234567890123456")) // 16 cyfr
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void rejects_formatting_characters() {
        assertThatThrownBy(() -> new PhoneNumber("+48 123 456 789"))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode").isEqualTo(INVALID_PHONE_NUMBER);
    }

    @Test
    void equal_values_are_equal() {
        assertThat(new PhoneNumber("+48123456789"))
                .isEqualTo(new PhoneNumber("+48123456789"))
                .hasSameHashCodeAs(new PhoneNumber("+48123456789"));
    }

    @Test
    void toString_returns_raw_value() {
        assertThat(new PhoneNumber("+48123456789")).hasToString("+48123456789");
    }
}