package pl.m22.gamehive.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOtherExceptions(Exception ex) {

        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);

        ApiError apiError = new ApiError(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(apiError);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBaseException(BaseException ex) {

        log.warn("BaseException occurred: {} ({})", ex.getMessage(), ex.getErrorCode());

        ApiError apiError = new ApiError(ex.getErrorCode().name(), ex.getMessage());
        HttpStatus status = ex.getErrorCode().getHttpStatus();
        return ResponseEntity.status(status).body(apiError);
    }
}
