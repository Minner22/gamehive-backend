package pl.m22.gamehive.common.domain;

import org.jspecify.annotations.NonNull;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.regex.Pattern;

public record Username(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");

    public Username {
        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_USERNAME, "Username must not be blank");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new DomainException(ErrorCode.INVALID_USERNAME, "Username must be 3-30 chars and contain only letters, digits, '.', '_' or '-'");
        }
    }

    @Override
    public @NonNull String toString() {

        return value;
    }
}
