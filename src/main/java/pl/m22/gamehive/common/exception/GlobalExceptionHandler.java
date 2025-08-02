package pl.m22.gamehive.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<String> handleUsernameAlreadyExists(UsernameAlreadyExistsException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<String> handleRoleNotFound(RoleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }

    @ExceptionHandler(UsernameOrEmailNotFoundException.class)
    public ResponseEntity<String> handleUsernameOrEmailNotFound(UsernameOrEmailNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<String> handleInvalidPassword(InvalidPasswordException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(JwtPrivateKeyLengthException.class)
    public ResponseEntity<String> handleJwtPrivateKeyLength(JwtPrivateKeyLengthException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidJwtSignature.class)
    public ResponseEntity<String> handleInvalidJwtSignature(InvalidJwtSignature ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidJwtTypeException.class)
    public ResponseEntity<String> handleInvalidJwtType(InvalidJwtTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ExpiredJwtTokenException.class)
    public ResponseEntity<String>  handleExpiredJwtToken(ExpiredJwtTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidJwtSubjectException.class)
    public ResponseEntity<String> handleInvalidJwtSubject(InvalidJwtSubjectException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidJwtRolesException.class)
    public ResponseEntity<String> handleInvalidJwtRoles(InvalidJwtRolesException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }


    @ExceptionHandler(InvalidJwtJtiException.class)
    public ResponseEntity<String> handleInvalidJwtJti(InvalidJwtJtiException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeJOSEException.class)
    public ResponseEntity<String> handleRuntimeJOSE(RuntimeJOSEException ex) {

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

    @ExceptionHandler(UserNotActivatedException.class)
    public ResponseEntity<String> handleUserNotActivated(UserNotActivatedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(EmailNotFoundException.class)
    public ResponseEntity<String> handleEmailNotFound(EmailNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyActivatedException.class)
    public ResponseEntity<String> handleUserAlreadyActivated(UserAlreadyActivatedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeParseException.class)
    public ResponseEntity<String> handleRuntimeParse(RuntimeParseException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An unexpected error occurred: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBaseException(BaseException ex) {
        ApiError apiError = new ApiError(ex.getErrorCode().name(), ex.getMessage());
        HttpStatus status = mapToHttpStatus(ex.getErrorCode());
        return ResponseEntity.status(status).body(apiError);
    }

    private HttpStatus mapToHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EMAIL_ALREADY_EXISTS -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR; // Default fallback
        };
    }
}
