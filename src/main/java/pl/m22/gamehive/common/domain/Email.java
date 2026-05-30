package pl.m22.gamehive.common.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.jspecify.annotations.NonNull;
import pl.m22.gamehive.common.exception.DomainException;
import pl.m22.gamehive.common.exception.ErrorCode;

import java.util.Set;

public record Email(String value) {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    public Email {

        if (value == null || value.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_EMAIL_FORMAT, "Email must not be blank");
        }

        Set<ConstraintViolation<EmailHolder>> violations = VALIDATOR.validate(new EmailHolder(value));

        if (!violations.isEmpty()) {
            throw new DomainException(ErrorCode.INVALID_EMAIL_FORMAT, "Invalid email format");
        }
    }

    public String obfuscated() {

        return value.replaceAll("(?<=.{2}).(?=.*@)", "*");
    }

    @Override
    public @NonNull String toString() {

        return value;
    }

    private record EmailHolder(
            @jakarta.validation.constraints.Email
            String email
    ) {
    }
}
