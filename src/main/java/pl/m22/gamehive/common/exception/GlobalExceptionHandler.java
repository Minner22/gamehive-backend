package pl.m22.gamehive.common.exception;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
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

        return errorBuilder(ErrorCode.VALIDATION_ERROR).body(apiError);
    }

    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(org.springframework.security.authorization.AuthorizationDeniedException ex) {

        log.warn("Access denied (method security): {}", ex.getMessage());

        return error(ErrorCode.ACCESS_DENIED);
    }

    // backstop dla wyścigów: find-or-create wydawcy/autora (UNIQUE) i TOCTOU guardów *_IN_USE (FK RESTRICT)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        log.warn("Data integrity conflict: {}", ex.getMessage());

        return error(ErrorCode.DATA_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOtherExceptions(Exception ex) {

        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);

        return error(ErrorCode.INTERNAL_ERROR);
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

        return error(ErrorCode.VALIDATION_ERROR);
    }

    // nieznana właściwość w ?sort= (Spring Data rzuca to dopiero przy wykonaniu zapytania). To błąd wejścia
    // klienta, nie awaria serwera: bez tego handlera literówka w parametrze wpadała w handleOtherExceptions,
    // czyli 500 + ERROR ze stack trace'em w logach. Dotyczy każdego stronicowanego endpointu w projekcie.
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownSortProperty(PropertyReferenceException ex) {

        log.warn("Unknown sort property: {}", ex.getMessage());

        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported HTTP method {} for {} (supported: {})",
                request.getMethod(), request.getRequestURI(), ex.getSupportedHttpMethods());

        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null) {
            headers.setAllow(supported);
        }

        return errorBuilder(ErrorCode.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(apiError(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {

        log.warn("No resource for request: {}", ex.getMessage());

        return error(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {

        log.warn("No acceptable representation for {} {} (Accept: {}, supported: {})",
                request.getMethod(), request.getRequestURI(),
                request.getHeader(HttpHeaders.ACCEPT), ex.getSupportedMediaTypes());

        return error(ErrorCode.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Unsupported Content-Type {} for {} {}",
                ex.getContentType(), request.getMethod(), request.getRequestURI());

        return error(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // brakujące / nieczytelne ciało żądania (pusty body, zły JSON) — bez tego wpada w handleOtherExceptions -> 500
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {

        log.warn("Unreadable request body: {}", ex.getMessage());

        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiError> handleMissingPathVariable(MissingPathVariableException ex) {

        log.error("Missing path variable (mapping defect): {}", ex.getMessage(), ex);

        return error(ErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> handleRequestBinding(ServletRequestBindingException ex) {

        log.warn("Request binding failure: {}", ex.getMessage());

        return error(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiError> handleMissingCookie(MissingRequestCookieException ex) {

        log.warn("Required cookie missing: {}", ex.getCookieName());

        return error(ErrorCode.REFRESH_TOKEN_MISSING);
    }

    private ResponseEntity<ApiError> buildResponse(BaseException ex) {

        return errorBuilder(ex.getErrorCode()).body(new ApiError(ex.getErrorCode().name(), ex.getMessage()));
    }

    private static ResponseEntity<ApiError> error(ErrorCode errorCode) {

        return errorBuilder(errorCode).body(apiError(errorCode));
    }

    private static ApiError apiError(ErrorCode errorCode) {

        return new ApiError(errorCode.name(), errorCode.getDefaultMessage());
    }


    private static ResponseEntity.BodyBuilder errorBuilder(ErrorCode errorCode) {

        return ResponseEntity.status(errorCode.getHttpStatus()).contentType(MediaType.APPLICATION_JSON);
    }
}
