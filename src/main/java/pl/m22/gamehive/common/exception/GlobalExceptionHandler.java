package pl.m22.gamehive.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiValidationError> handleValidationExceptions(MethodArgumentNotValidException ex) {

        log.warn("Validation error occurred: {}", ex.getMessage());

        List<FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        ApiValidationError apiError = new ApiValidationError(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                fieldErrors
        );

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(org.springframework.security.authorization.AuthorizationDeniedException ex) {

        log.warn("Access denied (method security): {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.ACCESS_DENIED.name(),
                ErrorCode.ACCESS_DENIED.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(apiError);
    }

    // backstop dla wyścigów: find-or-create wydawcy/autora (UNIQUE) i TOCTOU guardów *_IN_USE (FK RESTRICT)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        log.warn("Data integrity conflict: {}", ex.getMessage());

        ApiError apiError = new ApiError(ErrorCode.DATA_CONFLICT.name(), ErrorCode.DATA_CONFLICT.getDefaultMessage());

        return ResponseEntity.status(ErrorCode.DATA_CONFLICT.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOtherExceptions(Exception ex) {

        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);

        ApiError apiError = new ApiError(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomainException(DomainException ex) {

        log.info("Domain rule violation: {} ({})", ex.getMessage(), ex.getErrorCode());

        return buildResponse(ex);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiError> handleApplicationException(ApplicationException ex) {

        log.warn("Application flow error: {} ({})", ex.getMessage(), ex.getErrorCode());

        return buildResponse(ex);
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ApiError> handleInfrastructureException(InfrastructureException ex) {

        log.error("Infrastructure failure: {} ({})", ex.getMessage(), ex.getErrorCode(), ex);

        return buildResponse(ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError>  handleTypeMismatch(MethodArgumentTypeMismatchException ex) {

        log.warn("Path/param type mismatch: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(apiError);
    }

    // nieznana właściwość w ?sort= (Spring Data rzuca to dopiero przy wykonaniu zapytania). To błąd wejścia
    // klienta, nie awaria serwera: bez tego handlera literówka w parametrze wpadała w handleOtherExceptions,
    // czyli 500 + ERROR ze stack trace'em w logach. Dotyczy każdego stronicowanego endpointu w projekcie.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(PropertyReferenceException ex) {

        log.warn("Unknown sort property: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {

        log.warn("Unsupported HTTP method: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null) {                 // @Nullable: konstruktor 1-argumentowy zostawia null
            headers.setAllow(supported);
        }

        ApiError apiError = new ApiError(
                ErrorCode.METHOD_NOT_ALLOWED.name(),
                ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus()).headers(headers).body(apiError);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {

        log.warn("No resource for path: {}", ex.getResourcePath());

        ApiError apiError = new ApiError(
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {

        log.warn("No acceptable representation: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.NOT_ACCEPTABLE.name(),
                ErrorCode.NOT_ACCEPTABLE.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.NOT_ACCEPTABLE.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiError);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {

        log.warn("Unsupported Content-Type: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus()).body(apiError);
    }

    // brakujące / nieczytelne ciało żądania (pusty body, zły JSON) — bez tego wpada w handleOtherExceptions -> 500
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {

        log.warn("Unreadable request body: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                ErrorCode.VALIDATION_ERROR.name(),
                ErrorCode.VALIDATION_ERROR.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiError> handleMissingCookie(MissingRequestCookieException ex) {

        log.warn("Required cookie missing: {}", ex.getCookieName());

        ApiError apiError = new ApiError(
                ErrorCode.REFRESH_TOKEN_MISSING.name(),
                ErrorCode.REFRESH_TOKEN_MISSING.getDefaultMessage()
        );

        return ResponseEntity.status(ErrorCode.REFRESH_TOKEN_MISSING.getHttpStatus()).body(apiError);
    }

    private ResponseEntity<ApiError> buildResponse(BaseException ex) {

        ApiError apiError = new ApiError(ex.getErrorCode().name(), ex.getMessage());

        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(apiError);
    }
}
