package pl.m22.gamehive.common.domain;

import org.jspecify.annotations.NonNull;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.regex.Pattern;

public record PhoneNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

    public PhoneNumber {

        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_PHONE_NUMBER, "Phone number must not be blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.INVALID_PHONE_NUMBER, "Phone number must be in E.164 format (e.g. +48123456789)");
        }
    }

    @Override
    public @NonNull String toString() {
        return value;
    }
}
