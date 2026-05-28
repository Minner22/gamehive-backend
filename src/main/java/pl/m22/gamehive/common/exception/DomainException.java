package pl.m22.gamehive.common.exception;

/**
 * Naruszenie reguły biznesowej / invariantu domenowego. Oczekiwana odpowiedź
 * na formalnie poprawne, ale niedozwolone biznesowo działanie użytkownika.
 *
 * <p>Loguje na poziomie INFO — to nie incydent, nie błąd, nie wymaga alarmu.
 *
 * <p>Przykłady:
 * <ul>
 *   <li>{@link ErrorCode#EMAIL_ALREADY_EXISTS} — naruszenie unikalności emaila</li>
 *   <li>{@link ErrorCode#INVALID_PASSWORD} — błędne hasło przy logowaniu</li>
 *   <li>{@link ErrorCode#CANNOT_REMOVE_LAST_ADMIN} — naruszenie invariantu "minimum jeden admin"</li>
 * </ul>
 */

public class DomainException extends BaseException {
    public DomainException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DomainException(ErrorCode errorCode) {
        super(errorCode);
    }
}
