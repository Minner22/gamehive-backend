package pl.m22.gamehive.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "Email not found"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    USER_NOT_ACTIVATED(HttpStatus.FORBIDDEN, "User account is not activated"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Role not found"),
    IDENTIFIER_NOT_FOUND(HttpStatus.NOT_FOUND, "Identifier not found"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "Invalid password"),
    USER_ALREADY_ACTIVATED(HttpStatus.CONFLICT, "User is already activated"),

    JWT_KEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid JWT signing key"),
    JWT_SIGNING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign or verify JWT"),
    JWT_INVALID_ROLES(HttpStatus.UNAUTHORIZED, "JWT token does not contain valid roles"),
    JWT_INVALID_ALGORITHM(HttpStatus.UNAUTHORIZED, "Invalid JWT algorithm"),
    JWT_INVALID_SUBJECT(HttpStatus.UNAUTHORIZED, "JWT token does not contain a valid subject"),
    JWT_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Invalid JWT signature"),
    JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT token has expired"),
    JWT_INVALID_TYPE(HttpStatus.UNAUTHORIZED, "JWT token has invalid type"),
    JWT_INVALID_JTI(HttpStatus.UNAUTHORIZED, "Invalid or revoked JWT JTI"),
    JWT_PARSE_ERROR(HttpStatus.BAD_REQUEST, "Failed to parse JWT"),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
