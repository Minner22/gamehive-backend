package pl.m22.gamehive.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ===== Domain — naruszenia reguł biznesowych (handler loguje INFO) =====
    CANNOT_MODIFY_OWN_ACCOUNT(HttpStatus.FORBIDDEN, "Cannot modify your own account"),
    CANNOT_REMOVE_LAST_ADMIN(HttpStatus.CONFLICT, "Cannot remove last administrator"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "Invalid email format"),
    INVALID_HASHED_PASSWORD(HttpStatus.BAD_REQUEST, "Invalid password hash"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "Invalid password"),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "Invalid phone number"),
    INVALID_PROFILE_PICTURE_URL(HttpStatus.BAD_REQUEST, "Invalid profile picture URL"),
    INVALID_USERNAME(HttpStatus.BAD_REQUEST, "Invalid username"),
    ROLE_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Role already assigned to user"),
    USER_ALREADY_ACTIVATED(HttpStatus.CONFLICT, "User is already activated"),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Username already exists"),

    // ===== Application — problemy w przepływie use-case (handler loguje WARN) =====
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "Account is disabled"),
    EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "Email not found"),
    JWT_BLACKLISTED(HttpStatus.UNAUTHORIZED, "JWT token already used"),
    JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT token has expired"),
    JWT_INVALID_ALGORITHM(HttpStatus.UNAUTHORIZED, "Invalid JWT algorithm"),
    JWT_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Invalid JWT signature"),
    JWT_INVALID_TYPE(HttpStatus.UNAUTHORIZED, "JWT token has invalid type"),
    JWT_INVALID_JTI(HttpStatus.UNAUTHORIZED, "Invalid or revoked JWT JTI"),
    JWT_INVALID_SUBJECT(HttpStatus.UNAUTHORIZED, "JWT token does not contain a valid subject"),
    JWT_INVALID_ROLES(HttpStatus.UNAUTHORIZED, "JWT token does not contain valid roles"),
    JWT_PARSE_ERROR(HttpStatus.BAD_REQUEST, "Failed to parse JWT"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Role not found"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token has been revoked"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    USER_NOT_ACTIVATED(HttpStatus.FORBIDDEN, "User account is not activated"),
    USERNAME_OR_EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "Username or email not found"),

    // ===== Infrastructure — awarie systemów zewnętrznych / techniczne (handler loguje ERROR + stack) =====
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email"),
    JWT_KEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid JWT signing key"),
    JWT_SIGNING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to sign or verify JWT"),
    REDIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Token service temporarily unavailable"),

    // ===== Handler-only — nierzucane bezpośrednio, używane przez GlobalExceptionHandler =====
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed");


    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
