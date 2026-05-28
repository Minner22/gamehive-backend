package pl.m22.gamehive.common.exception;

import lombok.Getter;

/**
 * Bazowy wyjątek aplikacji niosący {@link ErrorCode}. {@link GlobalExceptionHandler}
 * mapuje go na odpowiedź HTTP (status z {@code errorCode.getHttpStatus()}, ciało jako
 * {@code ApiError}).
 *
 * <p>Wybierz konkretny podtyp zgodnie z naturą błędu:
 * <ul>
 *   <li>{@link DomainException} — naruszenie reguły biznesowej (loguje INFO)</li>
 *   <li>{@link ApplicationException} — problem w przepływie use-case (loguje WARN)</li>
 *   <li>{@link InfrastructureException} — awaria systemu zewnętrznego (loguje ERROR + stack)</li>
 * </ul>
 */

@Getter
public class BaseException extends RuntimeException {
    private  final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}
