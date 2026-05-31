package pl.m22.gamehive.common.domain;

import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.regex.Pattern;

public record HashedPassword(String value) {

    private static final Pattern HASH_FORMAT = Pattern.compile("^\\{[^}]+}.+");

    public HashedPassword {

        if (value == null || !HASH_FORMAT.matcher(value).matches()) {
            throw new DomainException(ErrorCode.INVALID_HASHED_PASSWORD, "Value does not look like an properly encoded password");
        }
    }

    public static HashedPassword fromRaw(String rawPassword, PasswordEncoder encoder) {

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_HASHED_PASSWORD, "Raw password must not be blank");
        }

        return new HashedPassword(encoder.encode(rawPassword));
    }

    public static HashedPassword fromHash(String alreadyHashed) {

        return new HashedPassword(alreadyHashed);
    }

    public boolean matches(String rawPassword, PasswordEncoder encoder) {

        return encoder.matches(rawPassword, this.value);
    }

    @Override
    public @NonNull String toString() {

        return "HashedPassword[***]";
    }
}
